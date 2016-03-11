package bg.uni.sofia.fmi.corejava.log.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LogServer implements Runnable, AutoCloseable {

	private int port;
	private Selector selector;
	private ByteBuffer readBuffer;
	private LogWorker worker;
	private ExecutorService newCachedPool;
	private Future<String> aRunnableGenId;
	private HashMap<SocketChannel, String> clientsId = new HashMap<>();

	private void initSelector() throws IOException {

		selector = Selector.open();

		ServerSocketChannel serverChannel = ServerSocketChannel.open();

		serverChannel.configureBlocking(false);

		ServerSocket serverSocket = serverChannel.socket();

		InetSocketAddress address = new InetSocketAddress(port);

		serverSocket.bind(address);

		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		readBuffer = ByteBuffer.allocate(1024);

		System.out.println("LogServer is listening on port " + port);

	}

	public LogServer(int port, LogWorker worker) throws IOException {
		this.port = port;
		this.initSelector();
		this.worker = worker;
		newCachedPool = Executors.newCachedThreadPool();
	}

	@Override
	public void run() {

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
					if (!key.isAcceptable()) {
						if (!clientsId.containsKey(key.channel())) {
							String id = (String) aRunnableGenId.get();
							clientsId.put((SocketChannel) key.channel(), id);
						}
					}
					if (key.isAcceptable()) {
						this.accept(key);
					} else if (key.isReadable()) {
						this.read(key);
					}
					it.remove();
				}
			} catch (Exception e) {
				System.out.println("Something went wrong with the connection!" + e.getMessage());
			}
		}
	}

	private void accept(SelectionKey key) throws IOException {

		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		socketChannel.register(this.selector, SelectionKey.OP_READ);

		System.out.println("Client " + socketChannel + " connected");

		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			System.out.println("Connection with client was interrupted!" + e.getMessage());
		}

		LogClientId<String> gen = new LogClientId<>();
		aRunnableGenId = newCachedPool.submit(gen);
		gen.processId(socketChannel, readBuffer);

	}

	private void read(SelectionKey key) throws IOException {

		SocketChannel socketChannel = (SocketChannel) key.channel();
		try {

			this.readBuffer.clear();

			int numRead;
			try {
				numRead = socketChannel.read(this.readBuffer);
			} catch (IOException e) {
				System.out.println("Broken Channel! It did not receive any messages" + e.getMessage());
				key.cancel();
				socketChannel.close();
				return;
			}

			if (numRead == -1) {
//				System.out.println("Client " + clientsId.get(socketChannel) + " quitted");
				key.channel().close();
				key.cancel();
				return;
			}

			this.worker.processData(socketChannel, this.readBuffer.array(), numRead, clientsId);
			this.worker = new LogWorker();
			new Thread(worker).start();
			System.out.println("Client " + socketChannel + " wrote " + numRead);

		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
			System.out.println("The channel is broken! Connection is attempting to close");
			// The channel is broken. Close it and cancel the key
			try {
				socketChannel.close();
			} catch (IOException e) {
				// Nothing that we can do
			}
			key.cancel();
			return;
		}
	}

	@Override
	public void close() throws Exception {
		if (selector != null) {
			try {
				selector.close();
				System.out.println("Server stopped");
			} catch (IOException e) {
				// Nothing that we can do
			}
		}
	}

	public static void main(String[] args) {
		try {
			LogWorker worker = new LogWorker();
			new Thread(worker).start();
			new Thread(new LogServer(10514, worker)).start();
		} catch (IOException e) {
			System.out.println("Failed to initiate server engine" + e.getMessage());
		}
	}

}
