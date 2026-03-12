package GUI;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.imageio.*;
import java.awt.image.*;
import java.net.*;

public class ImageLoader{

	public static String tmpDir = System.getProperty("java.io.tmpdir");;
	private static final String REMOTE_IMAGE_BASE = System.getProperty("hackwars.assets.baseUrl","");
	public static ImageIcon PCI_ICON;
	public static ImageIcon FOLDER_ICON;
	public static ImageIcon TEXT_ICON;
	public static ImageIcon FIREWALL_ICON;
	public static ImageIcon SCRIPT_ICON;
	public static ImageIcon IMAGE_ICON;
	public static File STATS;
	public static BufferedImage DUCT_TAPE;
	public static BufferedImage GERMANIUM;
	public static BufferedImage SILICON;
	public static BufferedImage YBCO;
	public static BufferedImage PLUTONIUM;
	
	public static void init(){
		PCI_ICON = getImageIcon("images/pci.png");
		FOLDER_ICON = getImageIcon("images/folderBig.png");
		TEXT_ICON = getImageIcon("images/textfile.png");
		FIREWALL_ICON = getImageIcon("images/firewallHome.png");
		SCRIPT_ICON = getImageIcon("images/scriptHome.png");
		IMAGE_ICON = getImageIcon("images/image.png");
		STATS = getFile("images/sidebar.png");
		DUCT_TAPE = getImage("images/ducttape.png");
		GERMANIUM = getImage("images/germanium.png");
		SILICON = getImage("images/silicon.png");
		YBCO = getImage("images/YBCO.png");
		PLUTONIUM = getImage("images/plutonium.png");
	}
	
	public static ImageIcon getImageIcon(String location){
		File F=new File(tmpDir+"/"+location);
		if(F.exists()){
			return new ImageIcon(tmpDir+"/"+location);
		}
		try{
			cacheImage(location);
			File cached=new File(tmpDir+"/"+location);
			if(cached.exists()){
				return new ImageIcon(cached.getAbsolutePath());
			}
		}catch(Exception e){
		}
		return(null);
	}
	
	public static File getFile(String location){
		File F=new File(tmpDir+"/"+location);
		if(F.exists()){
			return new File(tmpDir+"/"+location);
		}
		try{
			cacheImage(location);
			File cached=new File(tmpDir+"/"+location);
			if(cached.exists()){
				return cached;
			}
		}catch(Exception e){
		}
		return(null);
	}
	
	public static BufferedImage getImage(String location){
		try{
			File imageFile=getFile(location);
			if(imageFile!=null){
				return ImageIO.read(imageFile.toURI().toURL());
			}
		}catch(Exception e){
		}
		return null;
	}

	private static void cacheImage(String location) throws IOException{
		File outFile=new File(tmpDir+"/"+location);
		File parent=outFile.getParentFile();
		if(parent!=null&&!parent.exists()){
			parent.mkdirs();
		}
		if(cacheFromClasspath(location,outFile)){
			return;
		}
		if(REMOTE_IMAGE_BASE!=null&&REMOTE_IMAGE_BASE.trim().length()>0){
			cacheFromRemote(location,outFile);
		}
	}

	private static boolean cacheFromClasspath(String location,File outFile) throws IOException{
		InputStream in=ImageLoader.class.getClassLoader().getResourceAsStream(location);
		if(in==null){
			return false;
		}
		try{
			FileOutputStream out=new FileOutputStream(outFile);
			try{
				byte buf[]=new byte[256];
				int size=0;
				while((size=in.read(buf))>0){
					out.write(buf,0,size);
				}
			}finally{
				out.close();
			}
		}finally{
			in.close();
		}
		return true;
	}

	private static void cacheFromRemote(String location,File outFile) throws IOException{
		String base=REMOTE_IMAGE_BASE;
		if(!base.endsWith("/")){
			base+="/";
		}
		URL U=new URL(base+location);
		InputStream in=U.openStream();
		try{
			FileOutputStream out=new FileOutputStream(outFile);
			try{
				byte buf[]=new byte[256];
				int size=0;
				while((size=in.read(buf))>0){
					out.write(buf,0,size);
				}
			}finally{
				out.close();
			}
		}finally{
			in.close();
		}
	}
}
