package bg.uni.sofia.fmi.corejava.log.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;

@SuppressWarnings("hiding")
public class LogClientId<String> implements Callable<String> {

	private Queue<String> queue = new LinkedList<>();

	@Override
	public String call() throws Exception {
		while (true) {
			synchronized (queue) {
				while (queue.size() == 0) {
					try {
						queue.wait();

					} catch (InterruptedException e) {
						System.out.println("Something went wrong! Server didn't receive ID!");
					}
				}
			}
			System.out.println("ID: " + queue.peek());
			return queue.poll();

		}
	}

	@SuppressWarnings("unchecked")
	public void processId(SocketChannel socketChannel, ByteBuffer readBuffer) throws IOException {
		readBuffer.clear();
		int numRead = socketChannel.read(readBuffer);
		if (numRead == -1) {
			throw new IOException("Broken channel");
		}
		readBuffer.flip();
		byte[] data = new byte[readBuffer.limit()];
		int counter = 0;
		while (readBuffer.hasRemaining()) {
			data[counter++] = readBuffer.get();
		}
		String id = (String) new java.lang.String(data, StandardCharsets.UTF_8);
		queue.add(id);
		synchronized (queue) {
			queue.notifyAll();
		}
	}
}
