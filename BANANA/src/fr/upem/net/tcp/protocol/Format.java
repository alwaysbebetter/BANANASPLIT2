package fr.upem.net.tcp.protocol;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


public class Format {
	
	public static boolean isIPv4(String ip) {
	    if (ip == null || ip.isEmpty()) return false;
	    ip = ip.trim();

	    try {
	        Pattern pattern = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
	        Matcher matcher = pattern.matcher(ip);
	        return matcher.matches();
	    } catch (PatternSyntaxException ex) {
	        return false;
	    }
	}
	
	public static boolean isIPv6(String ip) {
	    if (ip == null || ip.isEmpty()) return false;
	    ip = ip.trim();

	    try {
	        Pattern pattern = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
	        Matcher matcher = pattern.matcher(ip);
	        return matcher.matches();
	    } catch (PatternSyntaxException ex) {
	        return false;
	    }
	}
	
	public static boolean isIPv6Compress(String ip) {
	    if (ip == null || ip.isEmpty()) return false;
	    ip = ip.trim();

	    try {
	        Pattern pattern = Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");
	        Matcher matcher = pattern.matcher(ip);
	        return matcher.matches();
	    } catch (PatternSyntaxException ex) {
	        return false;
	    }
	}
	
	public static boolean validIP(String ip){
		return isIPv4(ip) || isIPv6(ip) || isIPv6Compress(ip);
	}
	
	public static String getMyIP(){
		String ip;
	    try {
	        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
	        while (interfaces.hasMoreElements()) {
	            NetworkInterface iface = interfaces.nextElement();
	            // filters out 127.0.0.1 and inactive interfaces
	            if (iface.isLoopback() || !iface.isUp())
	                continue;

	            Enumeration<InetAddress> addresses = iface.getInetAddresses();
	            while(addresses.hasMoreElements()) {
	                InetAddress addr = addresses.nextElement();
	                ip = addr.getHostAddress();
	                if(validIP(ip))
	                	return ip;
	            }
	        }
	        return "";
	    } catch (SocketException e) {
	        throw new RuntimeException(e);
	    }
	}

}
