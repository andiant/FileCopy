/* FC_Timer.java
 Version 1.0
 Praktikum Rechnernetze HAW Hamburg
 Autor: M. Huebner
 */

public class FC_Timer extends Thread {
  /* Special timer for FileCopy Objects */
  private FileCopyClient myFCC;
  private long delay;
  private long seqNum;
  private boolean isCancelled;

  /**
   * Timeout value (delay) must be given in nanoseconds [see
   * System.nanoTime()]
   * 
   */
  public FC_Timer(long timeout, FileCopyClient fc, long seqNum) {
    this.delay = timeout; // nanoseconds
    this.myFCC = fc;
    this.seqNum = seqNum;
    this.isCancelled = false;
  }

  public void run() {
    /*
     * Timer sleeps until delay is over (--> timeoutTask - call!) or is
     * interrupted
     */
    long millis = delay / 1000000L;
    int nanos = (int) (delay % 1000000L);
    myFCC.testOut("FC_Timer started for packet: " + seqNum + " Delay: "
        + delay + "ns");

    try {
      sleep(millis, nanos);
    } catch (InterruptedException e) {
      /* Timer cancelled */
      isCancelled = true;
    }

    myFCC.testOut("FC_Timer stopped! Packet: " + seqNum);

    /* Perform task if not cancelled */
    if (!isCancelled) {
      myFCC.timeoutTask(seqNum);
    }
  }
}
