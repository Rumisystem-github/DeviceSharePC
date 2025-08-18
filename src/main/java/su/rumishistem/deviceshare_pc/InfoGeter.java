package su.rumishistem.deviceshare_pc;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class InfoGeter {
	private static OperatingSystemMXBean os_bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

							//↓loadよりuseの方が直感的じゃない？「「使用」率」なんだから
	public static int cpu_used() {
		double system_cpu_use = os_bean.getSystemCpuLoad(); 
		if (system_cpu_use < 0) return 0;

		return (int)Math.floor(system_cpu_use * 100);
	}

	public static long memory_max() {
		return os_bean.getTotalPhysicalMemorySize();
	}

	public static long memory_used() {
		//数値がおかしい気がする←どうやらキャッシュも含めてるらしい
		return memory_max() - os_bean.getFreePhysicalMemorySize();
	}
}
