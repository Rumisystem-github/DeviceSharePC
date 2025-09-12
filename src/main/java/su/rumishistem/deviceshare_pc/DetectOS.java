package su.rumishistem.deviceshare_pc;

import su.rumishistem.deviceshare_pc.Type.OS;

public class DetectOS {
	public static OS detect() {
		String os = System.getProperty("os.name").toUpperCase();

		if (os.contains("NUX") || os.contains("NIX")) {
			return OS.Linux;
		} else if (os.contains("WIN")) {
			return OS.Windows;
		} else {
			return OS.Undefined;
		}
	}
}
