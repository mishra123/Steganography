
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/*** Header description: ***	In bytes: (4)+ 3+ 1+ 1+ 4= 2+ 9 Bytes.  (*4 - for every 1 byte in () as every 4 bytes of of master file contain 2 bits)
 *  2 bytes representing the size of the actual file
 *  3 bytes indicate the version of the software used to embed message eg. 2 0 0
 *  1 Byte1 (4 actual bits) incicates features used which include file Compression, Encryption, Message/File
 *  1 byte contains the compression ratio
 *  4 bytes Contain the size of the data (A 32 bit integer value)
***/

public class Steganograph
{
	public static final String VERSION= "2.0.0";
	public static final byte[] VERSION_BYTE= {'2','0','0'};
	public static final int OFFSET_JPG= 3;
	public static final int OFFSET_PNG= 42;
	public static final int OFFSET_GIF_BMP_TIF= 32;
	public static final short HEADER_LENGTH= 15* 4;
	
         //  Three letters indicate:
	//  Uncompressed/Compressed  Encrypted/Unencrypted  Message/File
	public static final byte UUM= 0;
	public static final byte UUF= 1;
	public static final byte UEM= 2;
	public static final byte UEF= 3;
	public static final byte CUM= 4;
	public static final byte CUF= 5;
	public static final byte CEM= 6;
	public static final byte CEF= 7;

	private static Cipher cipher;

	private static SecretKeySpec spec;
	private static String masterExtension, message;
	private static AboutFrame about= new AboutFrame();

	private static File masterFile;
	
         // This byte stores the features being used by the file
	
        private static byte features;
	private static int inputFileSize;
	private static int i, j, inputOutputMarker, messageSize, tempInt;
	private static short compressionRatio= 0, temp;
	private static byte byte1, byte2, byte3, byteArrayIn[];
	private static ByteArrayOutputStream byteOut;

	private Steganograph()
	{
		System.out.println("Steganograph "+" ready...");
	}

	public static String getMessage()
	{
		return message;
	}

	// Embeds a message into a Master file
	public static boolean embedMessage(File masterFile, File outputFile, String msg, int compression, String password)
	{
		if(msg==null)
		{
			message= "Message is empty";
			return false;
		}
		if(msg.length()<1)
		{
			message= "Message is empty";
			return false;
		}

		if(password!= null && password.length()<8)
		{
			message= "Password should be minimum of 8 Characters";
			return false;
		}

		messageSize= msg.length();
		
		if(compression!= -1)
		{
			// Make sure that the compression is a valid numerical between 0 and 9
			if(compression< 0)		compression= 0;
			if(compression>9)		compression= 9;

			if(password== null)	features= CUM;
			else					features= CEM;
		}
		else
		{
			if(password== null)	features= UUM;
			else					features= UEM;
		}

		try
		{
			byteOut= new ByteArrayOutputStream();
			// Convert message into a character array
			byte []messageArray= msg.getBytes();
			messageSize= messageArray.length;
			inputFileSize= (int) masterFile.length();

			// create a byte array of length equal to size of input file
			byteArrayIn= new byte[inputFileSize];

			// Open the input file read all bytes into byteArrayIn
			DataInputStream in= new DataInputStream(new FileInputStream(masterFile));
			in.read(byteArrayIn, 0, inputFileSize);
			in.close();

			String fileName= masterFile.getName();
			masterExtension= fileName.substring(fileName.length()-3, fileName.length());

			if(masterExtension.equalsIgnoreCase("jpg"))
			{
				// Skip past OFFSET_JPG bytes
				byteOut.write(byteArrayIn, 0, OFFSET_JPG);
				inputOutputMarker= OFFSET_JPG;
			}
			else if(masterExtension.equalsIgnoreCase("png"))
					{
						// Skip past OFFSET_PNG bytes
						byteOut.write(byteArrayIn, 0, OFFSET_PNG);
						inputOutputMarker= OFFSET_PNG;
					}
				  else
					{
						// Skip past OFFSET_JPG_BMP_TIF bytes
						byteOut.write(byteArrayIn, 0, OFFSET_GIF_BMP_TIF);
						inputOutputMarker= OFFSET_GIF_BMP_TIF;
					}

			
                    // Convert the 32 bit input file size into byte array
			
                        byte tempByte[]= new byte[4];
			for(i=24, j=0; i>=0; i-=8, j++)
			{
				tempInt= inputFileSize;
				tempInt>>= i;
				tempInt&= 0x000000FF;
				tempByte[j]= (byte) tempInt;
			}
			// Embed 4 byte input File size array into the master file
			embedBytes(tempByte);

			// Write the remaining bytes
			byteOut.write(byteArrayIn, inputOutputMarker, inputFileSize- inputOutputMarker);
			inputOutputMarker= inputFileSize;

			// Embed the 3 byte version information into the file
			writeBytes(VERSION_BYTE);

			// Write 1 byte for features
			writeBytes(new byte[]{features});
		
			// Compress the message if required
			if(features== CUM || features== CEM)
			{
				ByteArrayOutputStream arrayOutputStream= new ByteArrayOutputStream();
				ZipOutputStream zOut= new ZipOutputStream(arrayOutputStream);
				ZipEntry entry= new ZipEntry("MESSAGE");
				zOut.setLevel(compression);
				zOut.putNextEntry(entry);

				zOut.write(messageArray, 0, messageSize);
				zOut.closeEntry();
				zOut.finish();
				zOut.close();
				
				// Get the compressed message byte array
				messageArray= arrayOutputStream.toByteArray();
				compressionRatio= (short) ((double)messageArray.length / (double)messageSize * 100.0);
				messageSize= messageArray.length;
			}

			// Embed 1 byte compression ratio into the output file
			writeBytes(new byte[]{(byte) compressionRatio});


			// Encrypt the message if required
			if(features== UEM || features== CEM)
			{
				Cipher cipher= Cipher.getInstance("DES");
				SecretKeySpec spec= new SecretKeySpec(password.substring(0, 8).getBytes(), "DES");
				cipher.init(Cipher.ENCRYPT_MODE, spec);
				messageArray= cipher.doFinal(messageArray);
				messageSize= messageArray.length;
			}

			// Convery the 32 bit message size into byte array
			tempByte= new byte[4];
			for(i=24, j=0; i>=0; i-=8, j++)
			{
				tempInt= messageSize;
				tempInt>>= i;
				tempInt&= 0x000000FF;
				tempByte[j]= (byte) tempInt;
			}
			// Embed 4 byte messageSize array into the master file
			writeBytes(tempByte);

			// Embed the message
			writeBytes(messageArray);

			DataOutputStream out= new DataOutputStream(new FileOutputStream(outputFile));
			//out.write(byteArrayOut, 0, byteArrayOut.length);
			byteOut.writeTo(out);
			out.close();
		}
		catch(EOFException e)
		{
		}
		catch(Exception e)
		{
			message= "Oops!!\nError: "+ e.toString();
			e.printStackTrace();
			return false;
		}

		message= "Message embedded successfully in file '"+ outputFile.getName()+ "'.";
		return true;
	}
	
	// Retrieves an embedded message from a Master file
	public static String retrieveMessage(SteganoInformation info, String password)
	{
		String messg= null;
		features= info.getFeatures();

		try
		{
			masterFile= info.getFile();
			byteArrayIn= new byte[(int) masterFile.length()];

			DataInputStream in= new DataInputStream(new FileInputStream(masterFile));
			in.read(byteArrayIn, 0, (int)masterFile.length());
			in.close();

			messageSize= info.getDataLength();

			if(messageSize<=0)
			{
				message= "Unexpected size of message: 0.";
				return("#FAILED#");
			}

			byte[] messageArray= new byte[messageSize];
			inputOutputMarker= info.getInputMarker();
			readBytes(messageArray);

			//Decrypt the message if required
			if(features== CEM || features== UEM)
			{
				password= password.substring(0, 8);
				byte passwordBytes[]= password.getBytes();				
				cipher= Cipher.getInstance("DES");
				spec= new SecretKeySpec(passwordBytes, "DES");
				cipher.init(Cipher.DECRYPT_MODE, spec);
				try
				{
					messageArray= cipher.doFinal(messageArray);
				}
				catch(Exception bp)
				{
					message= "Incorrent Password";
					bp.printStackTrace();
					return "#FAILED#";
				}
				messageSize= messageArray.length;
			}

			// Uncompress the message if required
			if(features== CUM || features== CEM)
			{
				ByteArrayOutputStream by= new ByteArrayOutputStream();
				DataOutputStream out= new DataOutputStream(by);

				ZipInputStream zipIn= new ZipInputStream(new ByteArrayInputStream(messageArray));
				zipIn.getNextEntry();
				byteArrayIn= new byte[1024];
				while((tempInt= zipIn.read(byteArrayIn, 0, 1024))!= -1)
					out.write(byteArrayIn, 0, tempInt);

				zipIn.close();
				out.close();
				messageArray= by.toByteArray();
				messageSize= messageArray.length;
			}

			messg= new String(SteganoInformation.byteToCharArray(messageArray));
		}
		catch(Exception e)
		{
			message= "Oops!!\n Error: "+ e;
			e.printStackTrace();
			return("#FAILED#");
		}

		message= "Message size: "+ messageSize+ " B";
		return messg;
	}

	// Embeds a file into a Master file
	public static boolean embedFile(File masterFile, File outputFile, File dataFile, int compression, String password)
	{
		messageSize= (int) dataFile.length();

		if(password!= null && password.length()<8)
		{
			message= "Password should be minimum of 8 Characters";
			return false;
		}

		if(compression!= 0)
		{
			// Make sure that the compression is a valid numerical between 0 and 9
			if(compression< 0)		compression= 0;
			if(compression>9)		compression= 9;

			if(password== null)	features= CUF;
			else					features= CEF;
		}
		else
		{
			if(password== null)	features= UUF;
			else					features= UEF;
		}

		inputFileSize= (int) masterFile.length();
		try
		{
			byteOut= new ByteArrayOutputStream();

			// create a byte array of length equal to size of input file
			byteArrayIn= new byte[inputFileSize];

			// Open the input file read all bytes into byteArrayIn
			DataInputStream in= new DataInputStream(new FileInputStream(masterFile));
			in.read(byteArrayIn, 0, inputFileSize);
			in.close();

			String fileName= masterFile.getName();
			masterExtension= fileName.substring(fileName.length()-3, fileName.length());

			if(masterExtension.equalsIgnoreCase("jpg"))
			{
				// Skip past OFFSET_JPG bytes
				byteOut.write(byteArrayIn, 0, OFFSET_JPG);
				inputOutputMarker= OFFSET_JPG;
			}
			else if(masterExtension.equalsIgnoreCase("png"))
					{
						// Skip past OFFSET_PNG bytes
						byteOut.write(byteArrayIn, 0, OFFSET_PNG);
						inputOutputMarker= OFFSET_PNG;
					}
				  else
					{
						// Skip past OFFSET_GIF_BMP_TIF bytes
						byteOut.write(byteArrayIn, 0, OFFSET_GIF_BMP_TIF);
						inputOutputMarker= OFFSET_GIF_BMP_TIF;
					}

			// Convert the 32 bit input file size into byte array
			byte tempByte[]= new byte[4];
			for(i=24, j=0; i>=0; i-=8, j++)
			{
				tempInt= inputFileSize;
				tempInt>>= i;
				tempInt&= 0x000000FF;
				tempByte[j]= (byte) tempInt;
			}
			// Embed 4 byte input File size array into the master file
			embedBytes(tempByte);

			// Write the remaining bytes
			byteOut.write(byteArrayIn, inputOutputMarker, inputFileSize- inputOutputMarker);
			inputOutputMarker= inputFileSize;

			// Embed the 3 byte version information into the file
			writeBytes(VERSION_BYTE);

			// Write 1 byte for features
			writeBytes(new byte[]{features});


			// Read the data bytes into fileArray
			byte []fileArray= new byte[messageSize];
			in= new DataInputStream(new FileInputStream(dataFile));
			in.read(fileArray, 0, messageSize);
			in.close();
			
			// Compress the message if required
			if(features== CUF || features== CEF)
			{
				ByteArrayOutputStream arrayOutputStream= new ByteArrayOutputStream();
				ZipOutputStream zOut= new ZipOutputStream(arrayOutputStream);
				ZipEntry entry= new ZipEntry(dataFile.getName());
				zOut.setLevel(compression);
				zOut.putNextEntry(entry);
				zOut.write(fileArray, 0, messageSize);
				zOut.closeEntry();
				zOut.finish();
				zOut.close();
				
				// Get the compressed message byte array
				fileArray= arrayOutputStream.toByteArray();
				compressionRatio= (short) ((double)fileArray.length / (double)messageSize * 100.0);
				messageSize= fileArray.length;
			}

			// Embed 1 byte compression ratio into the output file
			writeBytes(new byte[]{(byte) compressionRatio});


			// Encrypt the message if required
			if(features== UEF || features== CEF)
			{
				Cipher cipher= Cipher.getInstance("DES");
				SecretKeySpec spec= new SecretKeySpec(password.substring(0, 8).getBytes(), "DES");
				cipher.init(Cipher.ENCRYPT_MODE, spec);
				fileArray= cipher.doFinal(fileArray);
				messageSize= fileArray.length;
			}

			// Convert the 32 bit message size into byte array
			tempByte= new byte[4];
			for(i=24, j=0; i>=0; i-=8, j++)
			{
				tempInt= messageSize;
				tempInt>>= i;
				tempInt&= 0x000000FF;
				tempByte[j]= (byte) tempInt;
			}
			// Embed 4 byte messageSize array into the master file
			writeBytes(tempByte);

			// Embed the message
			writeBytes(fileArray);

			DataOutputStream out= new DataOutputStream(new FileOutputStream(outputFile));
			byteOut.writeTo(out);
			out.close();
		}
		catch(EOFException e)
		{
		}
		catch(Exception e)
		{
			message= "Oops!!\nError: "+ e.toString();
			e.printStackTrace();
			return false;
		}

		message= "File '"+ dataFile.getName()+ "' embedded successfully in file '"+ outputFile.getName()+ "'.";
		return true;
	}

	// Retrieves an embedded file from a Master file
	public static boolean retrieveFile(SteganoInformation info, String password, boolean overwrite)
	{
		File dataFile= null;
		features= info.getFeatures();

		try
		{
			masterFile= info.getFile();
			byteArrayIn= new byte[(int) masterFile.length()];

			DataInputStream in= new DataInputStream(new FileInputStream(masterFile));
			in.read(byteArrayIn, 0, (int)masterFile.length());
			in.close();

			messageSize= info.getDataLength();
			byte[] fileArray= new byte[messageSize];
			inputOutputMarker= info.getInputMarker();
			readBytes(fileArray);

			if(messageSize<=0)
			{
				message= "Unexpected size of embedded file: 0.";
				return false;
			}


			//Decrypt the file if required
			if(features== CEF || features== UEF)
			{
				password= password.substring(0, 8);
				byte passwordBytes[]= password.getBytes();				
				cipher= Cipher.getInstance("DES");
				spec= new SecretKeySpec(passwordBytes, "DES");
				cipher.init(Cipher.DECRYPT_MODE, spec);
				try
				{
					fileArray= cipher.doFinal(fileArray);
				}
				catch(Exception bp)
				{
					message= "Incorrect Password";
					bp.printStackTrace();
					return false;
				}
				messageSize= fileArray.length;
			}

			// Uncompress the file if required
			if(features== CUF || features== CEF)
			{
				ByteArrayOutputStream by= new ByteArrayOutputStream();
				DataOutputStream out= new DataOutputStream(by);

				ZipInputStream zipIn= new ZipInputStream(new ByteArrayInputStream(fileArray));
				ZipEntry entry= zipIn.getNextEntry();
				dataFile= new File(entry.getName());

				byteArrayIn= new byte[1024];
				while((tempInt= zipIn.read(byteArrayIn, 0, 1024))!= -1)
					out.write(byteArrayIn, 0, tempInt);

				zipIn.close();
				out.close();
				fileArray= by.toByteArray();
				messageSize= fileArray.length;
			}

			info.setDataFile(dataFile);
			if(dataFile.exists() && !overwrite)
			{
				message= "File Exists";
				return false;
			}

			DataOutputStream out= new DataOutputStream(new FileOutputStream(dataFile));
			out.write(fileArray, 0, fileArray.length);
			out.close();
		}
		catch(Exception e)
		{
			message= "Oops!!\n Error: "+ e;
			e.printStackTrace();
			return false;
		}

		message= "Retrieved file size: "+ messageSize+ " B";
		return true;
	}

	// Method used to write bytes into the output array
	private static void embedBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(int i=0; i< size; i++)
		{
			byte1= bytes[i];
			for(int j=6; j>=0; j-=2)
			{					
				byte2= byte1;
				byte2>>= j;
				byte2&= 0x03;

				byte3= byteArrayIn[inputOutputMarker];
				byte3&= 0xFC;
				byte3|= byte2;
				byteOut.write(byte3);
				inputOutputMarker++;
			}
		}
	}

	// Method used to write bytes into the output array
	private static void writeBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(int i=0; i< size; i++)
		{
			byteOut.write(bytes[i]);
			inputOutputMarker++;
		}
	}

	// Method used to retrieve bytes into the output array
	private static void retrieveBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(int i=0; i< size; i++)
		{
			byte1= 0;
			for(int j=6; j>=0; j-=2)
			{					
				byte2= byteArrayIn[inputOutputMarker];
				inputOutputMarker++;

				byte2&= 0x03;
				byte2<<= j;
				byte1|= byte2;
			}
			bytes[i]= byte1;
		}
	}

	// Method used to read bytes into the output array
	private static void readBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(int i=0; i< size; i++)
		{
			bytes[i]= byteArrayIn[inputOutputMarker];
			inputOutputMarker++;
		}
	}

	public static void showAboutDialog()
	{
		about.setDisplayed(true);
	}

	public static void updateUserInterface()
	{
		SwingUtilities.updateComponentTreeUI(about);
	}

	public static void showHelpDialog()
	{
		new HTMLFrame("file:"+ System.getProperty("user.dir") +
					System.getProperty("file.separator") + "help.html", false);
	}
	//*********************************************************************//
	
	// Inner class for description about Steganograph in a frame

	private static class AboutFrame extends JFrame implements ActionListener
	{
		private final Color GREEN= Color.green;
		private final Color YELLOW= Color.yellow;
		private final Color BLACK= Color.black;
		private JLabel lblTitle, lblImage, lblName, lblEmail, lblPhone;
		private JLabel filler1, filler2, filler3;
		private JButton btnVisit, btnComments, btnHelp, btnClose;
	
		private GridBagLayout gbl;
		private GridBagConstraints gbc;

		public AboutFrame()
		{
			setTitle("About Steganograph "+ Steganograph.VERSION);
			filler1= new JLabel(" "); filler2= new JLabel(" "); filler3= new JLabel(" ");
			lblTitle= new MyJLabel("Steganography Implementation "+ Steganograph.VERSION, new Font("Arial", Font.PLAIN, 15), YELLOW, BLACK);
			lblName= new MyJLabel("By:   Mohammad Mujeeb", new Font("Monotype Corsiva", Font.PLAIN, 14), GREEN, BLACK);
			lblEmail= new MyJLabel("      mohammad.mujeeb@gmail.com", GREEN, BLACK);
			lblPhone= new MyJLabel("      +91 9342204029", GREEN, BLACK);
			if(new File("Images/About").exists())
				lblImage= new JLabel(new ImageIcon("Images/Myself"));
			else
			{
				lblImage= new MyJLabel("Steganograph "+ Steganograph.VERSION, new Font("Times new roman", Font.PLAIN, 50), Color.red, Color.black);
				lblImage.setPreferredSize(new Dimension(222, 231));
			}
			btnVisit= new MyJButton("Author's Homepage", GREEN, BLACK);
			btnComments= new MyJButton("Send Comments", GREEN, BLACK);
			btnHelp= new MyJButton("Help", GREEN, BLACK);
			btnClose= new MyJButton("Close", GREEN, BLACK);

			btnVisit.addActionListener(this);
			btnHelp.addActionListener(this);
			btnClose.addActionListener(this);
			btnComments.addActionListener(this);

			JPanel panelAbout= new JPanel();
			panelAbout.setBackground(BLACK);
			panelAbout.setForeground(GREEN);
			gbl= new GridBagLayout();
			gbc= new GridBagConstraints();
			panelAbout.setLayout(gbl);;

			gbc.anchor= GridBagConstraints.CENTER;
			gbc.weightx= 2;
			gbc.weighty= 2;
			gbc.gridx= 1;	gbc.gridy= 1;	gbl.setConstraints(lblTitle, gbc);
			panelAbout.add(lblTitle);
			gbc.gridy= 2;	gbl.setConstraints(filler1, gbc);
			panelAbout.add(filler1);
			gbc.gridy= 3;	gbl.setConstraints(lblName, gbc);
			panelAbout.add(lblName);
			gbc.gridy= 4;	gbl.setConstraints(filler2, gbc);
			panelAbout.add(filler2);
			gbc.gridy= 5;	gbl.setConstraints(lblEmail, gbc);
			panelAbout.add(lblEmail);
			gbc.gridy= 6;	gbl.setConstraints(filler3, gbc);
			panelAbout.add(filler3);
			gbc.gridy= 7;	gbl.setConstraints(lblPhone, gbc);
			panelAbout.add(lblPhone);
			panelAbout= UtilityOperations.createBorderedPanel(panelAbout, "About Steganograph", 3, 3);

			JPanel panelUpper= new JPanel();
			panelUpper.setBackground(BLACK);
			panelUpper.setLayout(new FlowLayout());
			panelUpper.add(lblImage);
			panelUpper.add(new MyJLabel("      ", GREEN, BLACK));
			panelUpper.add(panelAbout);

			JPanel panelButtons= new JPanel();
			panelButtons.setBackground(Color.black);
			panelButtons.setLayout(new FlowLayout());
			panelButtons.setBackground(Color.black);
			panelButtons.add(btnComments);
			panelButtons.add(new JLabel("     "));
			panelButtons.add(btnHelp);
			panelButtons.add(new JLabel("     "));
			panelButtons.add(btnClose);

			JPanel mainPanel= new JPanel();
			mainPanel.setBackground(Color.black);
			gbl= new GridBagLayout();
			mainPanel.setLayout(gbl);
			gbc.gridx= 1;	gbc.gridy= 1;	gbl.setConstraints(panelUpper, gbc);
			mainPanel.add(panelUpper);
			gbc.gridy= 2;	gbl.setConstraints(panelButtons, gbc);
			mainPanel.add(panelButtons);
			mainPanel= UtilityOperations.createBorderedPanel(mainPanel, 3, 2);
			setContentPane(mainPanel);

			Dimension d= Toolkit.getDefaultToolkit().getScreenSize();
			int width= (int) (d.width*2.1/3);
			int height= (int) (d.height*1.9)/3;
			setSize(width, height);
			setLocation((d.width- width)/2, (d.height- height)/2);
			setResizable(false);
		}

		public void setDisplayed(boolean choice)
		{
			setVisible(choice);
		}

		public void actionPerformed(ActionEvent e)
		{
			if(e.getSource()== btnVisit)
				new HTMLFrame("http://mujeeb.addr.com", true);

			if(e.getSource()== btnHelp)
				new HTMLFrame("file:"+ System.getProperty("user.dir") +
					System.getProperty("file.separator") + "help.html", false);

			if(e.getSource()== btnComments)
				new HTMLFrame("http://mujeeb.addr.com/comments.html", true);

			if(e.getSource()== btnClose)			
				setVisible(false);
		}
	} 	// End of Class AboutFrame
}	// End of Class Steganograph


// Class to obtain information about a Steganograph file
class SteganoInformation
{
	private File file;
	private File dataFile= null;
	private String starter;
	private String version;
	private byte features;
	private short compressionRatio;
	private int dataLength, temp;
	private boolean isEster= false;

	private byte byteArray[], name[], byte1, byte2;
	private int inputMarker, i, j;

	// Accessor methods
	public File getFile() { return file; }
	public int getInputMarker() { return inputMarker; }
	public File getDataFile() { return dataFile; }
	public String getVersion() { return version; }
	public byte   getFeatures() { return features; }
	public short getCompressionRatio() { return compressionRatio; }
	public int   getDataLength()	{ return dataLength; }
	public boolean  isEster() 		{ return isEster; }

	// Mutator methods
	public void setDataFile(File dataFile)
	{
		this.dataFile= dataFile;
	}
	private void retrieveBytes(byte[] bytes, byte[] array, int marker)
	{
		byteArray= array;
		inputMarker= marker;

		int size= bytes.length;

		for(i=0; i< size; i++)
		{
			byte1= 0;
			for(j=6; j>=0; j-=2)
			{
				byte2= byteArray[inputMarker];
				inputMarker++;

				byte2&= 0x03;
				byte2<<= j;
				byte1|= byte2;
			}
			bytes[i]= byte1;
		}
	}

	private void retrieveBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(i=0; i< size; i++)
		{
			byte1= 0;
			for(j=6; j>=0; j-=2)
			{
				byte2= byteArray[inputMarker];
				inputMarker++;

				byte2&= 0x03;
				byte2<<= j;
				byte1|= byte2;
			}
			bytes[i]= byte1;
		}
	}

	private void readBytes(byte[] bytes, byte[] array, int marker)
	{
		byteArray= array;
		inputMarker= marker;

		int size= bytes.length;

		for(i=0; i< size; i++)
		{
			bytes[i]= byteArray[inputMarker];
			inputMarker++;
		}
	}

	private void readBytes(byte[] bytes)
	{
		int size= bytes.length;

		for(i=0; i< size; i++)
		{
			bytes[i]= byteArray[inputMarker];
			inputMarker++;
		}
	}

	public static char[] byteToCharArray(byte[] bytes)
	{
		int size= bytes.length, i;
		char []chars= new char[size];
		for(i=0; i<size; i++)
		{
			bytes[i]&= 0x7F;
			chars[i]= (char) bytes[i];
		}
		return chars;
	}

	public SteganoInformation(File file)
	{
		this.file= file;
		isEster= false;

		if(!file.exists())
		{
			starter= null;
			return;
		}

		if(file.getName().equals("Sec#x&y"))
		{
			isEster= true;
			return;
		}

		byteArray= new byte[(int) file.length()];
		try
		{
			DataInputStream in= new DataInputStream(new FileInputStream(file));
			in.read(byteArray, 0, (int) file.length());
			in.close();
		}
		catch(Exception e)
		{
			starter= null;
			return;
		}

		// Obtain the original file length
		name= new byte[4];

		String fileName= file.getName();
		String fileExtension= fileName.substring(fileName.length()-3, fileName.length());

		if(fileExtension.equalsIgnoreCase("jpg"))
			inputMarker= Steganograph.OFFSET_JPG;
		else if(fileExtension.equalsIgnoreCase("png"))
				inputMarker= Steganograph.OFFSET_PNG;
			  else
				inputMarker= Steganograph.OFFSET_GIF_BMP_TIF;

		retrieveBytes(name, byteArray, inputMarker);
		dataLength= 0;
		for(i=24,j=0; i>=0; i-=8,j++)
		{
			temp= name[j];
			temp&= 0x000000FF;
			temp<<= i;
			dataLength|= temp;
		}
		inputMarker= dataLength;

		if(dataLength<0 || dataLength>file.length())
		{
			starter= "Invalid";
			return;
		}
		else
			starter= "nid";

		// Retrive the name and version information
		byte versionArray[]= new byte[3];
		readBytes(versionArray, byteArray, inputMarker);
		char []versionTemp= byteToCharArray(versionArray);
		char []ver= new char[5];
		for(i=0, j=0; i<5; i++)
			if(i== 1 || i== 3)	ver[i]= '.';
			else
			{
				ver[i]= versionTemp[j++];
			}

		version= new String(ver);

		// Obtain the features
		name= new byte[1];
		readBytes(name);
		features= name[0];

		// Obtain the compression ratio
		readBytes(name);
		name[0]&= 0x7F;
		compressionRatio= name[0];

		// Obtain the data length
		name= new byte[4];
		readBytes(name);
		dataLength= 0;
		for(i=24,j=0; i>=0; i-=8,j++)
		{
			temp= name[j];
			temp&= 0x000000FF;
			temp<<= i;
			dataLength|= temp;
		}
	}

	public boolean isValid()
	{
		if(starter.equals("nid"))
		{	
			return true;
		}
		else
			return false;
	}
}

class HTMLFrame extends JFrame implements HyperlinkListener
{
	JEditorPane editorPane;
	JScrollPane scrollPane;

	public HTMLFrame(String startURL, boolean isOnline)
	{
		super("Help - Steganograph ");

		editorPane= new JEditorPane();
		editorPane.setEditable(false);
		if(isOnline)
		{
			editorPane.setBackground(Color.white);
			setTitle("Steganograph ");
		}
		//else
		//	editorPane.setBackground(Color.black);
		editorPane.addHyperlinkListener(this);
		scrollPane= new JScrollPane(editorPane);

		try
		{
			editorPane.setPage(startURL);
			getContentPane().add(scrollPane);
		}
		catch(Exception e)
		{
			JOptionPane.showMessageDialog(this, "Oops!! Error\n"+ e, "Error", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Dimension d= Toolkit.getDefaultToolkit().getScreenSize();
		setSize(d.width, d.height);
		setVisible(true);
	}

	public void hyperlinkUpdate(HyperlinkEvent e)
	{
		if(e.getEventType()== HyperlinkEvent.EventType.ACTIVATED)
		{
			try
			{
				editorPane.setPage(e.getURL());
			}
			catch(Exception ex)
			{
				JOptionPane.showMessageDialog(this, "Oops!! Error\n"+ ex, "Error", JOptionPane.WARNING_MESSAGE);
			}
		}
	}
}
