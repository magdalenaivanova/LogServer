package bg.uni.sofia.fmi.corejava.log.client;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class LogClient implements Runnable {

	private String remoteHost = null;
	private int remotePort = 0;

	private Selector selector;

	private ByteBuffer writeBuffer;

	private String id;
	private Scanner scan;

	private void tryReconnect() {
		try {
			this.selector.close();
			while (true) {
				try {
					this.initSelector();
					System.out.println("Recreated connection");
					break;
				} catch (IOException io) {
					// Nothing we can do
				}
			}
			this.run();
		} catch (IOException ioe) {
			System.out.println("Error! Could not manage to reconnect!" + ioe.getMessage());
			ioe.printStackTrace();
		}
	}

	private void initSelector() throws IOException {

		this.selector = Selector.open();

		SocketChannel socketChannel = SocketChannel.open();

		socketChannel.configureBlocking(false);

		InetSocketAddress address = new InetSocketAddress(this.remoteHost, this.remotePort);

		socketChannel.connect(address);

		socketChannel.register(selector, SelectionKey.OP_CONNECT);

		System.out.println("Client " + socketChannel + " connected to server");

	}

	public LogClient(String host, int port) throws IOException {
		this.remoteHost = host;
		this.remotePort = port;
		this.id = ManagementFactory.getRuntimeMXBean().getName();
		this.writeBuffer = ByteBuffer.allocate(1024);
		this.initSelector();
		this.scan = new Scanner(System.in);
	}

	@Override
	public void run() {
		boolean exit = false;
		while (true) {
			try {

				int num = this.selector.select();

				if (num == 0) {
					continue;
				}

				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				Iterator<SelectionKey> it = selectedKeys.iterator();

				while (it.hasNext()) {
					SelectionKey key = it.next();
					if (!key.isValid()) {
						continue;
					}
					if (key.isConnectable()) {
						this.connect(key);

					} else if (key.isWritable()) {
						exit = this.write(key);
					}
					it.remove();
				}
			} catch (Exception e) {
				System.out.println("Something went wrong!" + e.getMessage());
				this.tryReconnect();
			}
			if (exit) {
				break;
			}
		}
		System.out.println("Client stopped");
	}

	private void writeId(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		writeBuffer.clear();
		writeBuffer.put(id.getBytes());
		writeBuffer.flip();
		socketChannel.write(writeBuffer);

	}

	private boolean write(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		if (scan.hasNextLine()) {
			writeBuffer.clear();
			String message = scan.nextLine();
			if ("quit".equalsIgnoreCase(message.trim())) {
				return true;
			}
			writeBuffer.put(message.getBytes());
			writeBuffer.flip();
			socketChannel.write(writeBuffer);
		}

		return false;
	}

	private void connect(SelectionKey key) {

		SocketChannel socketChannel = (SocketChannel) key.channel();
		try {
			socketChannel.finishConnect();
			this.writeId(key);
		} catch (IOException e) {
			System.out.println("Could not manage to finish connection!" + e.getMessage());
			key.cancel();
			this.tryReconnect();
			return;
		}
		key.interestOps(SelectionKey.OP_WRITE);

	}

	public static void main(String[] args) {
		try {
			LogClient client = new LogClient("localhost", 10514);
			Thread t = new Thread(client);
			t.setDaemon(true);
			t.start();
			try {
				t.join();
			} catch (InterruptedException e) {
				System.out.println("Could not manage to start client application! " + e.getMessage());
			}
		} catch (IOException e) {
			System.out.println("Could not manage to start client application! " + e.getMessage());
		}

	}

}
