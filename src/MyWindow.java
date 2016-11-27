import java.net.DatagramPacket;
import java.util.Collections;
import java.util.LinkedList;

public class MyWindow {
	
	private static MyWindow instance;

	LinkedList<DatagramPacket> window;
	
	private MyWindow() {
		window = new LinkedList<DatagramPacket>();
	}
	
	public static MyWindow getInstance() {
		if(instance == null) {
			instance = new MyWindow();
		}
		return instance;
	}

	public synchronized boolean addLast(DatagramPacket packet) {
		while(window.size() >= 0) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				return false;
			}
		}
		return window.add(packet);
	}
	
}


