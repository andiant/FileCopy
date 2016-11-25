/* FileCopyServer.java
 Version 1.0
 Praktikum Rechnernetze HAW Hamburg
 Autor: M. Huebner
 */
import java.io.*;

import java.net.*;

import java.util.Collections;
import java.util.LinkedList;


public class FileCopyServer {
  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = true;
  public final static int SERVER_PORT = 23000;
  public final static int UDP_PACKET_SIZE = 1008;
  public final static int CONNECTION_TIMEOUT = 3000; // milliseconds
  public final static long DELAY = 10; // Propagation delay in ms

  // -------- Parameters (will be adjusted with values in the first packet)
  public int windowSize = 128;
  public String destPath = "";
  public long errorRate = 10000;

  // Each nth (n=ERROR_RATE) packet will be corrupted

  // -------- Socket structures
  private InetAddress clientAdress = null; // connection state
  private int clientPort = -1; // connection state
  private DatagramSocket serverSocket;
  private byte[] receiveData;
  private LinkedList<FCpacket> recBuf;

  // -------- Streams
  private FileOutputStream outToFile;

  // Protocol variables
  public long rcvbase;

  // Test error production
  private long recPacketCounter;

  // Constructor
  public FileCopyServer() {
    receiveData = new byte[UDP_PACKET_SIZE];
    recBuf = new LinkedList<FCpacket>();
  }

  public void runFileCopyServer() throws IOException {
    InetAddress receivedIPAddress;
    int receivedPort;
    DatagramPacket udpReceivePacket;
    FCpacket fcReceivePacket;
    boolean connectionEstablished = false;

    serverSocket = new DatagramSocket(SERVER_PORT);
    System.out.println("Waiting for connection using port " + SERVER_PORT);

    while (true) {
      try {
        udpReceivePacket = new DatagramPacket(receiveData, UDP_PACKET_SIZE);
        // Wait for data packet
        serverSocket.receive(udpReceivePacket);
        receivedIPAddress = udpReceivePacket.getAddress();
        receivedPort = udpReceivePacket.getPort();

        if (connectionEstablished == false) {
          // Establish new connection
          clientAdress = receivedIPAddress;
          clientPort = receivedPort;
          serverSocket.setSoTimeout(CONNECTION_TIMEOUT);
          connectionEstablished = true;
          rcvbase = 0;
          recPacketCounter = 0;
          errorRate = 10000;
          System.out.println("New connection established with " +
                             clientAdress.toString());
        }

        // Test if sender is the right one
        if ((clientAdress.equals(receivedIPAddress)) &&
              (clientPort == receivedPort)) {
          // extract sequence number and data
          fcReceivePacket = new FCpacket(udpReceivePacket.getData(),
                                         udpReceivePacket.getLength());

          long seqNum = fcReceivePacket.getSeqNum();
          recPacketCounter++;

          // Test on simulated error (packet checksum simulation)
          if ((errorRate > 0) && ((recPacketCounter % errorRate) == 0)) {
            testOut("---- Packet " + seqNum + " corrupted! ---------");
          } else {
            testOut("Server: Packet " + seqNum +
                    " correctly received! Expected for order delivery (rcvbase): " +
                    rcvbase);

            // Handle first packet --> read and set parameters
            if (seqNum == 0) {
              if (setParameters(fcReceivePacket)) {
                // open destination file
                outToFile = new FileOutputStream(destPath);
              } else {
                // Wrong parameter packet --> End!
                break;
              }
            }

            // Eval seq num
            if ((seqNum >= (rcvbase - windowSize)) &&
                  (seqNum < (rcvbase + windowSize))) {
              // ------ send ACK packet
              sendAck(fcReceivePacket);

              // Test whether packet is already delivered
              if (seqNum >= rcvbase) { // to deliver!
                insertPacketintoBuffer(fcReceivePacket);
                deliverBufferPackets(); // adjust rcvbase
              }
            }
          }
        }
      } catch (java.net.SocketTimeoutException e) {
        // Copy job successfully finished
        outToFile.close();
        connectionEstablished = false;
        System.out.println("Connection successfully closed, " +
                           recPacketCounter + " packets received, file " +
                           destPath + " saved!\n");
        // reset connection timeout
        serverSocket.setSoTimeout(0);
        System.out.println("Waiting for connection using port  " + SERVER_PORT);
      } catch (IOException e) {
        System.err.println("XXXXXXXXXXXXXXX File Error: " + destPath);

        break;
      }
    }

    // --------- End ------------------------
    serverSocket.close();
  }

  private void sendAck(FCpacket fcRcvPacket) {
    /* Create and send UDP packet with ACK seqNum */
    DatagramPacket udpAckPacket = new DatagramPacket(fcRcvPacket.getSeqNumBytes(),
                                                     8, clientAdress, clientPort);

    /* Anonymen Sende-Thread definieren und starten */
    new sendThread(udpAckPacket).start();
    testOut("ACK-Packet " + fcRcvPacket.getSeqNum() + " sent with delay " +
            DELAY);
  }

  private class sendThread extends Thread {
    /* Thread for sending of one ACK-Packet with propagation delay */
    DatagramPacket packet;

    public sendThread(DatagramPacket packet) {
      this.packet = packet;
    }

    public void run() {
      try {
        Thread.sleep(DELAY);
        serverSocket.send(packet);
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("Unexspected Error! " + e.toString());
        System.exit(-1);
      }
    }
  }

  private void insertPacketintoBuffer(FCpacket insertPacket) {
    /* Insert the packet into the receive buffer at the right position */
    if (!recBuf.contains(insertPacket)) { // no duplicates!
      recBuf.add(insertPacket);
      // sort in ascending order using the seq num
      Collections.sort(recBuf);
    }
  }

  private void deliverBufferPackets() throws IOException {
    /*
    * Deliver all packets which are in order, starting with rcvbase, remove
    * all delivered packets from the recBuffer and adjust the rcvbase
    * appropriately
    */
    while (!recBuf.isEmpty() && (recBuf.getFirst().getSeqNum() == rcvbase)) {
      writePacket(recBuf.getFirst());
      recBuf.removeFirst();
      rcvbase++;
    }
  }

  private void writePacket(FCpacket deliverPacket) throws IOException {
    /* Deliver single FCpacket: append packet data to outfile */

    // Packet 0 is control packet --> don't write to file!
    if (deliverPacket.getSeqNum() > 0) {
      outToFile.write(deliverPacket.getData(), 0, deliverPacket.getLen());

      testOut("Packet " + deliverPacket.getSeqNum() +
              " delivered! Block of length " + deliverPacket.getLen() +
              " appended to File " + destPath);
    }
  }

  private boolean setParameters(FCpacket controlPacket) {
    /* Evaluate packet with seqNum 0 */
    String parameters = "";
    String[] parameterArray;

    try {
      parameters = new String(controlPacket.getData(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    // Extract parameters
    parameterArray = parameters.split(";");

    if (parameterArray.length == 3) {
      // Adjust parameters
      destPath = parameterArray[0];

      try {
        windowSize = Integer.parseInt(parameterArray[1]);
        errorRate = Long.parseUnsignedLong(parameterArray[2]);
      } catch (NumberFormatException e) {
        System.err.println("Control Packet (seqNum 0): syntax error! No numeric parameter found: " +
                           parameters);

        // Parameter wrong
        return false;
      }

      System.out.println("Server-Parameters set: " + destPath +
                         " - WindowSize: " + windowSize + " - ErrorRate: " +
                         errorRate);

      // Parameter OK!
      return true;
    } else {
      System.err.println("Control Packet (seqNum 0) has wrong number of parameters: " +
                         parameters);

      // Parameter wrong
      return false;
    }
  }

  private void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      System.err.println("Server: " + out);
    }
  }

  public static void main(String[] argv) throws Exception {
    FileCopyServer myServer = new FileCopyServer();
    myServer.runFileCopyServer();
  }
}
