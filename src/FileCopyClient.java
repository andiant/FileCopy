
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
import java.net.SocketAddress;
import java.net.SocketException;
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
	private long timeoutValue = 100000000L;

	// ... ToDo
	private final char TRENNREICHEN = ';';
	private DatagramSocket serverSocket;
	private SocketAddress serverAdress;
	private LinkedList<FCpacket> window;
	private AckThread ackThread;

	// Constructor
	public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg, String windowSizeArg,
			String errorRateArg) {
		servername = serverArg;
		sourcePath = sourcePathArg;
		destPath = destPathArg;
		windowSize = Integer.parseInt(windowSizeArg);
		serverErrorRate = Long.parseLong(errorRateArg);

		window = new LinkedList<FCpacket>();
	}

	public void runFileCopyClient() {

		// ToDo!!

		// Datei einlesen
		MyFileReader fileReader = new MyFileReader(sourcePath);

		// Verbindung zum Server aufbauen
		try {
			serverSocket = new DatagramSocket(SERVER_PORT, InetAddress.getByName(servername));
		} catch (SocketException e1) {
			e1.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		ackThread = new AckThread();
		ackThread.start();

		// erstes Datenpacket senden
		FCpacket firstFCPacket = makeControlPacket();
		DatagramPacket sendPacket = new DatagramPacket(firstFCPacket.getData(), firstFCPacket.getLen());

		try {
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("Could not send first Packet!!!");
		}

		// Weitere Datenpackete schicken
		byte[] nextBytesFromFile;
		byte[] nextBytesToSend;
		FCpacket lastFcPacket = firstFCPacket;
		FCpacket newFcPacket;
		long currentWindowSize;
		long seqNum;
		while ((nextBytesFromFile = fileReader.nextBytes()) != null && nextBytesFromFile.length > 0) {
			seqNum = lastFcPacket.getSeqNum() + lastFcPacket.getLen();
			newFcPacket = new FCpacket(seqNum, nextBytesFromFile, nextBytesFromFile.length);

			// Prüfen/Warten, dass das neue Packet ins Window passt
			synchronized (window) {
				currentWindowSize = window.getLast().getSeqNum() - window.getFirst().getSeqNum();
			}
			while (currentWindowSize >= windowSize) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				synchronized (window) {
					currentWindowSize = window.getLast().getSeqNum() - window.getFirst().getSeqNum();
				}
			}

			// Neues Packet senden
			synchronized (window) {
				window.add(newFcPacket);
			}
			nextBytesToSend = newFcPacket.getSeqNumBytesAndData();
			try {
				serverSocket.send(new DatagramPacket(nextBytesToSend, nextBytesToSend.length));
			} catch (IOException e) {
				e.printStackTrace();
			}
			startTimer(newFcPacket);
			lastFcPacket = newFcPacket;
		}

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
				while (serverSocket.isConnected()) {
					udpReceivePacket = new DatagramPacket(receiveData, UDP_PACKET_SIZE);
					serverSocket.receive(udpReceivePacket);
					
					// TODO

				}

			} catch (Exception e) {
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
		testOut("Cancel Timer for packet" + packet.getSeqNum());

		if (packet.getTimer() != null) {
			packet.getTimer().interrupt();
		}
	}

	/**
	 * Implementation specific task performed at timeout
	 */
	public void timeoutTask(long seqNum) {
		//get FCPacket from window
		FCpacket packetToSend = null;
		for(int i = 0; i < window.size(); i++){
			if(window.get(i).getSeqNum() == seqNum){
				packetToSend = window.get(i);
				break;
			}
		}
		
		if(packetToSend != null){
			try {
				serverSocket.send(new DatagramPacket(packetToSend.getData(), packetToSend.getLen()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		startTimer(packetToSend);
	}

	/**
	 *
	 * Computes the current timeout value (in nanoseconds)
	 */
	public void computeTimeoutValue(long sampleRTT) {

		// ToDo
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
