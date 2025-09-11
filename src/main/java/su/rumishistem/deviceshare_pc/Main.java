package su.rumishistem.deviceshare_pc;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Main {
	private static final int tcp_port = 13818;
	private static final int udp_port = 13817;
	private static final String tcp_server_host = "deviceshare.rumiserver.com";
	private static final String udp_server_host = "192.168.100.120";

	public static String ID = UUID.randomUUID().toString();
	public static String token = "a";

	public static void main(String[] args) throws NoSuchAlgorithmException, UnknownHostException, IOException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		ID = args[0];
		token = args[1];

		//鍵生成
		String key_type = "RSA";
		KeyPairGenerator kpg = KeyPairGenerator.getInstance(key_type);
		KeyPair kp = kpg.generateKeyPair();
		//byte[] sk = kp.getPrivate().getEncoded();
		byte[] pk = kp.getPublic().getEncoded();

		//サーバーと鍵効果
		PublicKey server_pk = tcp_handshake(pk, key_type, ID, token);
		if (server_pk == null) return;
		System.out.println("TCPおけ");

		Cipher cp = Cipher.getInstance(key_type+"/ECB/PKCS1Padding");
		cp.init(Cipher.ENCRYPT_MODE, server_pk);

		//udp.setSoTimeout(1000);で実質1秒ごと実行になる
		while (true) {
			byte[][] send_body_list = {
				write_cpu_used(),
				write_memory()
			};

			//UDPに送りつける
			DatagramSocket udp = new DatagramSocket();
			udp.setSoTimeout(1000);

			//順番に送信
			for (int i = 0; i < send_body_list.length; i++) {
				byte[] body = send_body_list[i];

				//暗号化
				ByteArrayOutputStream data_baos = new ByteArrayOutputStream();
				write_data_and_length(data_baos, token.getBytes("UTF-8"));
				write_data_and_length(data_baos, body);
				byte[] encrypt_data = cp.doFinal(data_baos.toByteArray());

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				write_data_and_length(baos, ID.getBytes());
				write_data_and_length(baos, encrypt_data);

				//送信
				byte[] send_data = baos.toByteArray();
				DatagramPacket send_packet = new DatagramPacket(send_data, send_data.length, InetAddress.getByName(udp_server_host), udp_port);
				udp.send(send_packet);
			}

			//サーバーからのデータを受信
			try {
				byte[] receive_buffer = new byte[1024];
				DatagramPacket receive_packet = new DatagramPacket(receive_buffer, receive_buffer.length);
				udp.receive(receive_packet);

				//0xF0の場合、サーバーからのハンドシェイクやり直せ命令
				if ((receive_packet.getData()[0] & 0xFF) == 0xF0) {
					System.out.println("ハンドシェイクをやり直せと言われた");
					udp.close();
					main(args);
					return;
				}
			} catch (SocketTimeoutException EX) {
				//タイムアウトなので無視
			}

			//切断
			udp.close();
		}
	}

	private static byte[] write_cpu_used() throws IOException {
		int cpu_used = InfoGeter.cpu_used();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(0x01);//0x01=CPU使用率
		write_int(baos, cpu_used);

		return baos.toByteArray();
	}

	private static byte[] write_memory() throws IOException {
		long memory_max = InfoGeter.memory_max();
		long memory_used = InfoGeter.memory_used();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(0x02);//0x02=メモリー
		write_long(baos, memory_max);
		write_long(baos, memory_used);

		return baos.toByteArray();
	}

	private static void write_data_and_length(ByteArrayOutputStream baos, byte[] data) throws IOException {
		ByteBuffer length_buffer = ByteBuffer.allocate(4);
		length_buffer.order(ByteOrder.BIG_ENDIAN);
		length_buffer.putInt(data.length);
		baos.write(length_buffer.array());

		baos.write(data);
	}

	private static void write_int(ByteArrayOutputStream baos, int number) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(number);
		baos.write(buffer.array());
	}

	private static void write_long(ByteArrayOutputStream baos, long number) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putLong(number);
		baos.write(buffer.array());
	}

	private static PublicKey tcp_handshake(byte[] pk, String key_type, String id, String token) throws UnknownHostException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		SSLSocket socket = (SSLSocket) factory.createSocket(tcp_server_host, tcp_port);

		socket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2", "TLSv1.1"});

		PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		try {
			//へろー
			out.println("HELO");
			String l1 = in.readLine();
			if (!l1.startsWith("201")) return null;

			//ログイン
			out.println("LOGIN "+Base64.getEncoder().encodeToString(pk)+" "+key_type+" "+id+" "+token);
			String l2 = in.readLine();
			if (!l2.startsWith("200")) return null;
			Matcher mtc_key = Pattern.compile("<(.*)>").matcher(l2.split(" ")[1]);
			Matcher mtc_type = Pattern.compile("<(.*)>").matcher(l2.split(" ")[2]);
			if (mtc_key.find() && mtc_type.find()) {
				String server_pk_base64 = mtc_key.group(1);
				String server_pk_type = mtc_type.group(1);

				//サーバーの公開鍵をこねくり回す
				byte[] server_pk_byte = Base64.getDecoder().decode(server_pk_base64);
				X509EncodedKeySpec spec = new X509EncodedKeySpec(server_pk_byte);
				KeyFactory server_factory = KeyFactory.getInstance(server_pk_type);
				PublicKey server_pk = server_factory.generatePublic(spec);

				return server_pk;
			} else {
				return null;
			}
		} finally {
			//終了
			out.println("QUIT");
			out.close();
			in.close();
			socket.close();
		}
	}
}
