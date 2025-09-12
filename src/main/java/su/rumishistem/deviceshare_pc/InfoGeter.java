package su.rumishistem.deviceshare_pc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import su.rumishistem.deviceshare_pc.Type.OS;

public class InfoGeter {
	private static final Path linux_memory_path = Path.of("/proc/meminfo");
	private static final Path linux_cpu_path = Path.of("/proc/stat");

							//↓loadよりuseの方が直感的じゃない？「「使用」率」なんだから
	public static int cpu_used() throws IOException, InterruptedException {
		int use = 0;

		if (DetectOS.detect() == OS.Linux) {
			long[] old_stats = get_linux_cpu_stats();
			Thread.sleep(500);
			long[] new_stats = get_linux_cpu_stats();

			long old_idle = old_stats[3] + old_stats[4];
			long new_idle = new_stats[3] + new_stats[4];
			long old_total = 0;
			long new_total = 0;

			for (long v:old_stats) old_total += v;
			for (long v:new_stats) new_total += v;

			long total_diff = new_total - old_total;
			long idle_diff = new_idle - old_idle;

			double use_double = (double)(total_diff - idle_diff) / total_diff * 100.0;
			use = (int) Math.round(use_double);
		}

		return use;
	}

	private static long[] get_linux_cpu_stats() throws IOException {
		List<String> meminfo = Files.readAllLines(linux_cpu_path);
		for (String line:meminfo) {
			if (line.toUpperCase().startsWith("CPU")) {
				String[] parts = line.trim().split("\\s+");
				long[] val_list = new long[parts.length -1];
				for (int i = 1; i < parts.length; i++) {
					val_list[i - 1] = Long.parseLong(parts[i]);
				}

				return val_list;
			}
		}

		throw new RuntimeException(linux_cpu_path.toString() + "読み込みエラー");
	}

	public static long memory_max() throws IOException {
		long max = 0;

		if (DetectOS.detect() == OS.Linux) {
			List<String> meminfo = Files.readAllLines(linux_memory_path);
			for (String line:meminfo) {
				if (line.toUpperCase().startsWith("MEMTOTAL")) {
					String total = line.replaceAll("\\D+", "");
					max = Long.parseLong(total) * 1024;
					break;
				}
			}
		}

		return max;
	}

	public static long memory_used() throws IOException {
		long total = memory_max();
		long free = -1;
		long buffer = -1;
		long cached = -1;

		long use = 0;

		if (DetectOS.detect() == OS.Linux) {
			List<String> meminfo = Files.readAllLines(linux_memory_path);
			for (String line:meminfo) {
				if (line.toUpperCase().startsWith("MEMFREE")) {
					//フリー
					String s = line.replaceAll("\\D+", "");
					free = Long.parseLong(s) * 1024;
				} else if (line.toUpperCase().startsWith("BUFFERS")) {
					//バッファー
					String s = line.replaceAll("\\D+", "");
					buffer = Long.parseLong(s) * 1024;
				} else if (line.toUpperCase().startsWith("CACHED")) {
					//キャッシュ
					String s = line.replaceAll("\\D+", "");
					cached = Long.parseLong(s) * 1024;
				}
			}

			if (free == -1 || buffer == -1 || cached == -1) throw new RuntimeException("メモリ計算に必要な値がない");

			//計算
			use = total - free - buffer - cached;
		}

		return use;
	}
}
