package eu.siacs.conversations.xmpp.jingle;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;

import android.util.Log;
import android.widget.Button;

public class SocksConnection {
	private Socket socket;
	private String host;
	private String jid;
	private String cid;
	private int port;
	private boolean isProxy = false;
	private String destination;
	private OutputStream outputStream;
	private InputStream inputStream;
	private boolean isEstablished = false;

	public SocksConnection(JingleConnection jingleConnection, Element candidate, boolean initating) {
		this.cid = candidate.getAttribute("cid");
		this.host = candidate.getAttribute("host");
		this.port = Integer.parseInt(candidate.getAttribute("port"));
		String type = candidate.getAttribute("type");
		this.jid = candidate.getAttribute("jid");
		this.isProxy = "proxy".equalsIgnoreCase(type);
		try {
			MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
			StringBuilder destBuilder = new StringBuilder();
			destBuilder.append(jingleConnection.getSessionId());
			if (initating) {
				destBuilder.append(jingleConnection.getAccountJid());
				destBuilder.append(jingleConnection.getCounterPart());
			} else {
				destBuilder.append(jingleConnection.getCounterPart());
				destBuilder.append(jingleConnection.getAccountJid());
			}
			mDigest.reset();
			this.destination = CryptoHelper.bytesToHex(mDigest
					.digest(destBuilder.toString().getBytes()));
		} catch (NoSuchAlgorithmException e) {

		}
	}

	public void connect(final OnSocksConnection callback) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					socket = new Socket(host, port);
					inputStream = socket.getInputStream();
					outputStream = socket.getOutputStream();
					byte[] login = { 0x05, 0x01, 0x00 };
					byte[] expectedReply = { 0x05, 0x00 };
					byte[] reply = new byte[2];
					outputStream.write(login);
					inputStream.read(reply);
					if (Arrays.equals(reply, expectedReply)) {
						String connect = "" + '\u0005' + '\u0001' + '\u0000' + '\u0003'
								+ '\u0028' + destination + '\u0000' + '\u0000';
						outputStream.write(connect.getBytes());
						byte[] result = new byte[2];
						inputStream.read(result);
						int status = result[1];
						if (status == 0) {
							Log.d("xmppService", "established connection with "+host + ":" + port
									+ "/" + destination);
							isEstablished = true;
							callback.established();
						} else {
							callback.failed();
						}
					} else {
						socket.close();
						callback.failed();
					}
				} catch (UnknownHostException e) {
					callback.failed();
				} catch (IOException e) {
					callback.failed();
				}
			}
		}).start();
		
	}

	public void send(final JingleFile file, final OnFileTransmitted callback) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				FileInputStream fileInputStream = null;
				try {
					MessageDigest digest = MessageDigest.getInstance("SHA-1");
					digest.reset();
					fileInputStream = new FileInputStream(file);
					int count;
					byte[] buffer = new byte[8192];
					while ((count = fileInputStream.read(buffer)) > 0) {
						outputStream.write(buffer, 0, count);
						digest.update(buffer, 0, count);
					}
					outputStream.flush();
					file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
					if (callback!=null) {
						callback.onFileTransmitted(file);
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					try {
						if (fileInputStream != null) {
							fileInputStream.close();
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}).start();
		
	}
	
	public void receive(final JingleFile file, final OnFileTransmitted callback) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					MessageDigest digest = MessageDigest.getInstance("SHA-1");
					digest.reset();
					inputStream.skip(45);
					file.getParentFile().mkdirs();
					file.createNewFile();
					FileOutputStream fileOutputStream = new FileOutputStream(file);
					long remainingSize = file.getExpectedSize();
					byte[] buffer = new byte[8192];
					int count = buffer.length;
					while(remainingSize > 0) {
						Log.d("xmppService","remaning size:"+remainingSize);
						if (remainingSize<=count) {
							count = (int) remainingSize;
						}
						count = inputStream.read(buffer, 0, count);
						if (count==-1) {
							Log.d("xmppService","end of stream");
						} else {
							fileOutputStream.write(buffer, 0, count);
							digest.update(buffer, 0, count);
							remainingSize-=count;
						}
					}
					fileOutputStream.flush();
					fileOutputStream.close();
					file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
					Log.d("xmppService","transmitted filename was: "+file.getAbsolutePath());
					callback.onFileTransmitted(file);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	public boolean isProxy() {
		return this.isProxy;
	}

	public String getJid() {
		return this.jid;
	}
	
	public String getCid() {
		return this.cid;
	}

	public void disconnect() {
		if (this.socket!=null) {
			try {
				this.socket.close();
				Log.d("xmppService","cloesd socket with "+this.host);
			} catch (IOException e) {
				Log.d("xmppService","error closing socket with "+this.host);
			}
		}
	}
	
	public boolean isEstablished() {
		return this.isEstablished;
	}
}
