package bg.uni.sofia.fmi.corejava.log.server;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class LogWorker implements Runnable {

	private Queue<String> queue = new LinkedList<>();
	private Map<SocketChannel, String> id;
	private SocketChannel socketChannel;
	private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
	private final Path logFile = Paths.get("logs", "newlog.log");

	public void processData(SocketChannel socketChannel, byte[] data, int numRead, HashMap<SocketChannel, String> id) {
		byte[] dataCopy = new byte[numRead];
		this.id = id;
		this.socketChannel = socketChannel;
		System.arraycopy(data, 0, dataCopy, 0, numRead);
		String s = new String(dataCopy, Charset.defaultCharset());
		synchronized (queue) {
			queue.add(s);
			queue.notifyAll();
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				synchronized (queue) {
					while (queue.size() == 0) {
						try {
							queue.wait();
						} catch (InterruptedException e) {
							System.out.println("Server interuption!" + e.getMessage());
						}
					}

					FileChannel outChannel = null;
					try (FileOutputStream outputFile = new FileOutputStream(logFile.toString(), true);) {

						outChannel = outputFile.getChannel();
						ByteBuffer buff = ByteBuffer.allocate(8192);

						Calendar now = Calendar.getInstance();

						String nowAsString = tsFormat.format(now.getTime());

						String msg = queue.poll();

						String loggerLine = nowAsString + " [ " + this.id.get(socketChannel) + " ]: " + msg + "\n";
						buff.put(loggerLine.getBytes());

						buff.flip();
						while (buff.hasRemaining()) {
							try {
								outChannel.write(buff);
							} catch (IOException e) {
								System.out.println("Failed to record in .log file! " + e.getMessage());
							}
						}
						buff.clear();

					} catch (FileNotFoundException e) {
						System.out.println("File not found!");
					} catch (IOException e1) {
						System.out.println("Connection failed! " + e1.getMessage());
					} finally {
						try {
							outChannel.close();
						} catch (IOException e) {
							System.out.println(e.getMessage());
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

}
