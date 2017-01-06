
/* FileCopyClient.java
 Version 0.1 - Muss ergaenzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class FileCopyClient extends Thread {

	// -------- Constants
	public final static boolean TEST_OUTPUT_MODE = true;

	public final int SERVER_PORT = 23000;

	public final int UDP_PACKET_SIZE = 1008;

	// -------- Public parms
	public String servername;

	public String sourcePath;

	public String destPath;

	public int windowSize;

	public long serverErrorRate;

	// -------- Variables
	// current default timeout in nanoseconds
	private long timeoutValue = 200000000L;

	// TODO
	private DatagramSocket serverSocket;
	private InetAddress serverAdress;
	private LinkedList<FCpacket> window;
	private AckThread ackThread;
	private boolean done;
	
	// Programmauswertung
	private long gesamtUEbertragungszeit; // a) Gesamtübertragungszeit für die gerade übertragene Datei
	private int timerAblaeufe; // b) Anzahl an Timerabläufen und wiederholten Übertragungen
	private int wiederholteSendung;
	private int empfangeneBestaetigungen; // c) Anzahl an empfangenen Bestätigungen
	private double rttMittelwert; // d) der ermittelte Mittelwert für die RTT aller ACKs

	// Constructor
	public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg, String windowSizeArg,
			String errorRateArg) {
		
		//Programmauswertungs-Variablen
		timerAblaeufe = 0;
		wiederholteSendung = 0;
		empfangeneBestaetigungen = 0;
		rttMittelwert = 0.0;
		
		servername = serverArg;
		sourcePath = sourcePathArg;
		destPath = destPathArg;
		windowSize = Integer.parseInt(windowSizeArg);
		serverErrorRate = Long.parseLong(errorRateArg);

		window = new LinkedList<FCpacket>();
	}

	public void runFileCopyClient() {
		System.out.println("- - - - - - - - - - - - - - - - - - - - - - - -");
		System.out.println("Window-Size: " + windowSize + " \tFehler-Rate: " + serverErrorRate);
		System.out.println("");
		
		gesamtUEbertragungszeit = System.currentTimeMillis();
		
		// Datei einlesen
		MyFileReader fileReader = new MyFileReader(sourcePath);

		// Verbindung zum Server aufbauen
		try {
			serverAdress = InetAddress.getByName(servername);
			serverSocket = new DatagramSocket();
		} catch(UnknownHostException e) {
			System.out.println(servername + " ist nicht erreichbar");
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("ENDE wegen Fehler");
			System.exit(0);
		}
		done = false;
		ackThread = new AckThread();
		ackThread.start();

		// erstes Datenpacket senden
		FCpacket firstFCPacket = makeControlPacket();
		DatagramPacket sendPacket = new DatagramPacket(firstFCPacket.getSeqNumBytesAndData(), firstFCPacket.getLen() + 8,serverAdress,SERVER_PORT);

		try {
			window.add(firstFCPacket);
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("Could not send first Packet!!!");
			ackThread.interrupt();
			System.exit(0);
		}
		firstFCPacket.setTimestamp(System.nanoTime());
		startTimer(firstFCPacket);

		// Weitere Datenpackete schicken
		byte[] nextBytesFromFile;
		byte[] nextBytesToSend;
		FCpacket newFcPacket;
		long nextSeqNum = firstFCPacket.getSeqNum() + 1;
		while ((nextBytesFromFile = fileReader.nextBytes()) != null && nextBytesFromFile.length > 0) {
			newFcPacket = new FCpacket(nextSeqNum, nextBytesFromFile, nextBytesFromFile.length);

			// Prüfen/Warten, dass das neue Packet ins Window passt
			synchronized (window) {
				while (window.size() >= windowSize) {
					try {
						window.wait();
					} catch (InterruptedException e) {
						testOut("wait interrupted");
					}
				}

				// Neues Packet senden
				window.add(newFcPacket);
				nextBytesToSend = newFcPacket.getSeqNumBytesAndData();
				try {
					serverSocket.send(
							new DatagramPacket(nextBytesToSend, nextBytesToSend.length, serverAdress, SERVER_PORT));
				} catch (IOException e) {
					e.printStackTrace();
				}
				newFcPacket.setTimestamp(System.nanoTime());
				startTimer(newFcPacket);
				nextSeqNum++;
			}
		}
		done = true;
		try {
			ackThread.join();
		} catch (InterruptedException e) {
		}
		gesamtUEbertragungszeit = System.currentTimeMillis() - gesamtUEbertragungszeit;
		
		System.out.println("GesamtübertragungsZeit:\t" + gesamtUEbertragungszeit + " ms");
		System.out.println("Timer-Ausläufe:\t\t" + timerAblaeufe);
		System.out.println("Empfangsbestätigungen:\t" + empfangeneBestaetigungen);
		rttMittelwert = rttMittelwert / empfangeneBestaetigungen;
		System.out.println("RTT-Mittelwert:\t\t" + rttMittelwert);
		System.out.println("- - - - - - - - - - - - - - - - - - - - - - - -");
	}

	private class AckThread extends Thread {

		private byte[] receiveData;

		public AckThread() {
			receiveData = new byte[UDP_PACKET_SIZE];
		}

		@Override
		public void run() {
			try {
				DatagramPacket udpReceivePacket;
				FCpacket fcPacket;
				while (!done || !window.isEmpty()) {
					udpReceivePacket = new DatagramPacket(receiveData, UDP_PACKET_SIZE);
					serverSocket.receive(udpReceivePacket);
					long rtt = System.nanoTime();/*receive Time*/

					synchronized (window) {
						// fcPacket zum vergleich erstellen
						fcPacket = new FCpacket(udpReceivePacket.getData(), udpReceivePacket.getLength());
						// fcPacket umwandeln in das echte Datenpacket
						int fcPacketIndex = window.indexOf(fcPacket);
						if (fcPacketIndex != -1) {
							fcPacket = window.get(fcPacketIndex);
							// Packet bestätigen und Packet-Timer stoppen
							fcPacket.setValidACK(true);
							empfangeneBestaetigungen++;
							cancelTimer(fcPacket);
							rtt = rtt - fcPacket.getTimestamp();
							rttMittelwert += rtt;
							computeTimeoutValue(rtt);
							// Window verschieben
							if (fcPacket.equals(window.getFirst())) {
								while (fcPacket != null && fcPacket.isValidACK()) {
									window.removeFirst();
									if (!window.isEmpty()) {
										fcPacket = window.getFirst();
									} else {
										fcPacket = null;
									}
								}
								// Mainthread benachrichtigen, falls der wegen vollem window
								// wartet
								window.notifyAll();
						}
					}
					}

				}
			} catch (Exception e) {
				e.printStackTrace();
				serverSocket.close();
			}
		}

	}

	/**
	 *
	 * Timer Operations
	 */
	public void startTimer(FCpacket packet) {
		/* Create, save and start timer for the given FCpacket */
		FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
		packet.setTimer(timer);
		timer.start();
	}

	public void cancelTimer(FCpacket packet) {
		/* Cancel timer for the given FCpacket */
//		testOut("Cancel Timer for packet" + packet.getSeqNum());

		if (packet.getTimer() != null) {
			packet.getTimer().interrupt();
		}
	}

	/**
	 * Implementation specific task performed at timeout
	 */
	public void timeoutTask(long seqNum) {
		// get FCPacket from window
		FCpacket packetToSend = null;

		synchronized (window) {
			timerAblaeufe++;
			for(int i = 0; i < window.size(); i++){
				if(window.get(i).getSeqNum() == seqNum){
					packetToSend = window.get(i);
					break;
				}

			}
		}

		if (packetToSend != null) {
			try {
				wiederholteSendung = wiederholteSendung + 1;
				serverSocket.send(new DatagramPacket(packetToSend.getSeqNumBytesAndData(), packetToSend.getLen() + 8,serverAdress,SERVER_PORT));
				timeoutValue = timeoutValue * 2;
				startTimer(packetToSend);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 *
	 * Computes the current timeout value (in nanoseconds)
	 */
	private long jitter = -1;
	private long expRTT;
	private double x = 0.25;
	private double y = x/2;;
	public void computeTimeoutValue(long sampleRTT) {
//		(2.3) When a subsequent RTT measurement R' is made, a host MUST set
//        RTTVAR <- (1 - beta) * RTTVAR + beta * |SRTT - R'|
//        SRTT <- (1 - alpha) * SRTT + alpha * R'
		
		if (jitter < 0) {
			jitter = sampleRTT / 2;
			expRTT = sampleRTT;
		} else {
			jitter = (long) ((1.0 - x) * jitter + x * Math.abs(sampleRTT - expRTT));
			expRTT = (long) ((1.0 - y) * expRTT + y * sampleRTT);
			timeoutValue = expRTT + 4 * jitter;
		}
	}

	/**
	 *
	 * Return value: FCPacket with (0 destPath;windowSize;errorRate)
	 */
	public FCpacket makeControlPacket() {
		/*
		 * Create first packet with seq num 0. Return value: FCPacket with (0
		 * destPath ; windowSize ; errorRate)
		 */
		String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
		byte[] sendData = null;
		try {
			sendData = sendString.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return new FCpacket(0, sendData, sendData.length);
	}

	public void testOut(String out) {
		if (TEST_OUTPUT_MODE) {
			System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread.currentThread().getName(), out);
		}
	}

	public static void main(String argv[]) throws Exception {
		FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2], argv[3], argv[4]);
		myClient.runFileCopyClient();
	}

}
