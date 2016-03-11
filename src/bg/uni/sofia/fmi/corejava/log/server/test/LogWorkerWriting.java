package bg.uni.sofia.fmi.corejava.log.server.test;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import bg.uni.sofia.fmi.corejava.log.server.LogWorker;

public class LogWorkerWriting {
	
	@Before
	public void delete() throws IOException{
		final Path logFile = Paths.get("logs", "newlog.log");
		Files.deleteIfExists(logFile);
	}
	
	@SuppressWarnings("static-access")
	@Test
	// @Ignore
	public void shouldContainDataInFile() throws IOException, InterruptedException {
		final Path logFile = Paths.get("logs", "newlog.log");

		LogWorker worker = new LogWorker();
		Thread t = new Thread(worker);
		t.start();
		String id = ManagementFactory.getRuntimeMXBean().getName();
		Selector selector = Selector.open();
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		InetSocketAddress address = new InetSocketAddress("localhost", 10514);
		socketChannel.connect(address);
		socketChannel.register(selector, SelectionKey.OP_CONNECT);
		Random r = new Random();
		int bytes = r.nextInt(100) + 1;
		byte[] data = new byte[bytes];
		r.nextBytes(data);
		HashMap<SocketChannel, String> env = new HashMap<>();
		env.put(socketChannel, id);

		worker.processData(socketChannel, data, bytes, env);
		t.sleep(10);

		try (BufferedReader reader = new BufferedReader(new FileReader(logFile.toString()))) {
			String line;
			String s = new String(data, Charset.defaultCharset());
			while ((line = reader.readLine()) != null) {
				if (line.contains(env.get(socketChannel)) && line.contains(s)) {
					assertTrue(line.contains(env.get(socketChannel)));
					assertTrue(line.contains(s));
				}
			}

		} catch (Exception e) {
			System.out.println("File not exists");
		}
	}

	@Test
	// @Ignore
	public void shouldCreateFile() throws IOException {

		LogWorker worker = new LogWorker();
		String id = ManagementFactory.getRuntimeMXBean().getName();
		Selector selector = Selector.open();
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		InetSocketAddress address = new InetSocketAddress("localhost", 10514);
		socketChannel.connect(address);
		socketChannel.register(selector, SelectionKey.OP_CONNECT);

		Random r = new Random();
		int bytes = 500;
		byte[] data = new byte[bytes];
		HashMap<SocketChannel, String> env = new HashMap<>();
		env.put(socketChannel, id);

		for (int i = 0; i < 200; i++) {
			Thread t = new Thread(worker);
			t.start();
			r.nextBytes(data);
			worker.processData(socketChannel, data, bytes, env);
		}

		Path path = Paths.get("logs", "newlog.log");
		assertTrue(Files.exists(path));
	}

}
