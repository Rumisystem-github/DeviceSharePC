package su.rumishistem.deviceshare_pc;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import java.nio.file.AccessDeniedException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import javax.crypto.*;
import com.fasterxml.jackson.databind.*;
import su.rumishistem.rumi_java_lib.Ajax.*;

public class Main {
	public static final int tcp_port = 13818;
	public static final int udp_port = 13817;
	private static final String dir_path = "/etc/deviceshare";
	private static final String token_path = "/etc/deviceshare/token";
	private static String http_server_host = "deviceshare.rumiserver.com";
	public static String tcp_server_host = "deviceshare.rumiserver.com";
	public static String udp_server_host = "192.168.100.120";

	public static String ID = UUID.randomUUID().toString();
	public static String token = "a";

	public static void main(String[] args) throws NoSuchAlgorithmException, UnknownHostException, IOException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InterruptedException {
		if (!Files.exists(Path.of(dir_path))) {
			try {
				Files.createDirectories(Path.of(dir_path));
			} catch (AccessDeniedException ex) {
				System.out.println(dir_path + "にディレクトリを作成できませんでした");
				return;
			}
		}

		boolean start = false;

		for (String arg:args) {
			if (arg.equals("--dev")) {
				http_server_host = "deviceshare.beta.rumiserver.com";
			} else if (arg.equals("start")) {
				start = true;
			} else {
				System.out.println("引数が不適切です");
				return;
			}
		}

		if (!Files.exists(Path.of(token_path))) {
			//初期設定
			System.out.println("初期設定を開始します。");
			System.out.println("https://deviceshare.rumiserver.com/ で、[ﾃﾞﾊﾞｲｽを登録]を押下し、");
			System.out.println("登録IDを入手してください。");
			System.out.println();

			Scanner scanner = new Scanner(System.in);
			System.out.print("登録ID [ﾘﾀｰﾝｷｰで続行]>");
			String regist_id = scanner.nextLine().trim();

			LOG(LOG_TYPE.OK, "登録ID「"+regist_id+"」で受け付けました。");
			LOG(LOG_TYPE.PROCESS, "ｻｰﾊﾞｰと通信中です、お待ち下さい。");

			Ajax ajax = new Ajax("https://" + http_server_host + "/api/Device");
			AjaxResult result = ajax.POST(("{\"ID\": \""+regist_id+"\"}").getBytes());

			if (result.get_code() == 200) {
				LOG(LOG_TYPE.PROCESS_END_OK, "");
			} else if (result.get_code() == 404) {
				LOG(LOG_TYPE.PROCESS_END_FAILED, "");
				LOG(LOG_TYPE.FAILED, "登録IDが間違っているかも？");
				return;
			} else {
				LOG(LOG_TYPE.PROCESS_END_FAILED, "");
				LOG(LOG_TYPE.FAILED, "ｻｰﾊﾞｰがｴﾗｰを返しました("+result.get_code()+")");
				return;
			}

			JsonNode body = new ObjectMapper().readTree(result.get_body_as_string());

			StringBuilder sb = new StringBuilder();
			sb.append(body.get("ID").asText()).append("\n");
			sb.append(body.get("TOKEN").asText()).append("\n");

			Files.createFile(Path.of(token_path));
			Files.write(Path.of(token_path), sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);

			LOG(LOG_TYPE.OK, "初期設定が完了しました！");

			

			scanner.close();
		}

		if (start) {
			//起動
			String token_file = new String(Files.readAllBytes(Path.of(token_path)), StandardCharsets.UTF_8);
			String[] token_split = token_file.split("\n");
			ID = token_split[0];
			token = token_split[1];
			Sender.start();
		}
	}
}
