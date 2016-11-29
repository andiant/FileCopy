import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MyFileReader {
	
	private final int BYTE_DATA_LENGTH = 3;
	private FileInputStream fis = null;
	private BufferedInputStream bis = null;
	private File file;
	
	private int fileSize;
	private int remainingSize; 
	
	private boolean finished;
	

	// als String wird Pfad inkl. DateiName verstanden
	public MyFileReader(String fileName) {
		file = new File(fileName);
		fileSize = (int) file.length();
		remainingSize = fileSize;
		finished = false;
		try {
			fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public byte[] nextBytes(){
		
		if(finished){
			return new byte[0];
		}
		
		try{
			byte[] byteArr;
			if(remainingSize > BYTE_DATA_LENGTH){
				byteArr = new byte[BYTE_DATA_LENGTH];
				bis.read(byteArr, 0, BYTE_DATA_LENGTH);
				remainingSize -= BYTE_DATA_LENGTH;
			}else{
				byteArr = new byte[remainingSize];
				bis.read(byteArr, 0, remainingSize);
				finished = true;
				close();
			}
			return byteArr;
			
		}catch(IOException e){
			return null;
		}
	}
	
	private void close() throws IOException {
		fis.close();
		bis.close();
	}

	public static void main(String[] args) throws IOException {
		
		MyFileReader mfr = new MyFileReader("test.txt");
		byte[] bytes;
		while((bytes = mfr.nextBytes()).length != 0){
			for(byte b: bytes){
				System.out.println(b);
			}
			System.out.println("######################\n");
		}
	}
}