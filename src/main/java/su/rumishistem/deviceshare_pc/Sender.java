package su.rumishistem.deviceshare_pc;

import static su.rumishistem.deviceshare_pc.Main.ID;
import static su.rumishistem.deviceshare_pc.Main.token;
import static su.rumishistem.deviceshare_pc.Main.tcp_port;
import static su.rumishistem.deviceshare_pc.Main.udp_port;
import static su.rumishistem.deviceshare_pc.Main.udp_server_host;
import static su.rumishistem.deviceshare_pc.Main.tcp_server_host;
import static su.rumishistem.deviceshare_pc.Main.tcp_tls_enable;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.regex.*;
import javax.crypto.*;
import javax.net.ssl.*;

public class Sender {
	public static void start() throws NoSuchAlgorithmException, UnknownHostException, InvalidKeySpecException, IOException, InvalidKeyException, InterruptedException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException {
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

		while (true) {
			byte[][] send_body_list = {
				write_cpu_used(),
				write_memory()
			};

			//UDPに送りつける
			DatagramSocket udp = new DatagramSocket();

			//↓で実質1秒ごと実行になる
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
					start();
					return;
				}
			} catch (SocketTimeoutException EX) {
				//タイムアウトなので無視
			}

			//切断
			udp.close();
		}
	}

	private static byte[] write_cpu_used() throws IOException, InterruptedException {
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
		PrintWriter out = null;
		BufferedReader in = null;

		SSLSocket ssl_socket = null;
		Socket plain_socket = null;

		if (tcp_tls_enable) {
			SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			ssl_socket = (SSLSocket) factory.createSocket(tcp_server_host, tcp_port);
			ssl_socket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2", "TLSv1.1"});

			out = new PrintWriter(ssl_socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(ssl_socket.getInputStream()));
		} else {
			plain_socket = new Socket(tcp_server_host, tcp_port);
			out = new PrintWriter(plain_socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(plain_socket.getInputStream()));
		}

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

			if (tcp_tls_enable) {
				ssl_socket.close();
			} else {
				plain_socket.close();
			}
		}
	}
}
