
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Scanner;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fazecast.jSerialComm.*;

/**
 * This class will start the Multi-factor authenication program.
 * The Arduino File will Get the ID of the fingerprint + the RFID ID
 * First I will ask for a Username/Password, then check the RFID, then get the Fingerprint ID
 * I will check if the Username/Password matches one in the "login" page.
 * If so, then I will await to receive an RFID tag.
 * When I receive one, it will then check if it is the correct one associate with that account
 * If the RFID is incorrect, then it will throw an error.
 * IF the RFID is correct, it will then procreed to the Fingerprint ID.
 * Here I will receive an ID (int) from Arduino.
 * If the ID matches what I have saved, then it will work.
 * 
 * @author keith
 *
 */
public class Main extends JFrame implements ActionListener {

	    private JTextField usernameField;
	    private JPasswordField passwordField;
	    private JButton submitButton;
	    private JButton signupButton;
	    private JLabel messageLabel;
	    private JLabel errorLabel;
	    private Scanner scanner;
	    private File file;
	    private SerialPort nanoPort;
	    private boolean awaitingResponse, secondResponse;
	    private String RFID, fingerPrint;
		private byte[] buffer = new byte[1];
		private final int RFIDMODE = 1, FINGERPRINTMODE = 2, SIGNUPRFID = 3, SIGNUPFINGERPRINT = 4; 


	    public Main() {
	        setTitle("Login Page");
	        setSize(600, 200);
	        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        setLayout(new GridLayout(4, 1));

	        usernameField = new JTextField(20);
	        passwordField = new JPasswordField(20);
	        submitButton = new JButton("Submit");
	        signupButton = new JButton("Sign Up");
	        messageLabel = new JLabel("");
	        errorLabel = new JLabel("");
	        awaitingResponse = true;
	        secondResponse = false;
	        RFID = "";
	        fingerPrint = "";
	        
	        submitButton.addActionListener(this);
	        signupButton.addActionListener(this);

	        add(new JLabel("Username:"));
	        add(usernameField);
	        add(new JLabel("Password:"));
	        add(passwordField);
	        add(submitButton);
	        add(signupButton);
	        add(messageLabel);
	        add(errorLabel);

	        setVisible(true);
	    }

	    @Override
	    public void actionPerformed(ActionEvent e) {

	    	if (e.getSource() == submitButton) {
	        	errorLabel.setText("");
	            String username = usernameField.getText();
	            String password = new String(passwordField.getPassword());
	            RFID = "";
	            fingerPrint = "";
	            
	            if(username.length() < 1 || password.length() < 1) {
	            	messageLabel.setText("Provide both a username and password");
	            	return;
	            }
	            
	            // Authentication logic
	            // Search to see if the username matches any name in the Users.txt file
	            // Then check if the password given matches
	            
	            file = new File("src/Users.txt");
		        try {
					scanner = new Scanner(file);
				} catch (FileNotFoundException exception) {
					// TODO Auto-generated catch block
					exception.printStackTrace();
				}
		        String currentUser = "";
		        String[] attributes = null;
		        boolean correctPassword = false;
		        while(scanner.hasNextLine()) {
		        	attributes = null; // Reset in case the same username but different password
			        currentUser = scanner.nextLine();
			        String currentName = currentUser.substring(0, currentUser.indexOf(':'));
			        if(username.equals(currentName)) {
		            	attributes = currentUser.split(":");
		            	if(password.equals(attributes[1])) {
		            		correctPassword = true;
		            		break;
		            	}
		            	continue;
			        }
		        }
		        
		        
		        
	            if (correctPassword == false) {
	                messageLabel.setText("Invalid username or password.");
	            } else {
	            	// Remove the comma from attribute 3
			        attributes[3] = attributes[3].substring(0, attributes[3].length()-1);
	                messageLabel.setText("Awaiting arduino information!");
	                // Code to share Arduino Information with Java be implemented
	                
	                // While arduino not connected, don't proceed
	        		SerialPort[] array = SerialPort.getCommPorts();
	                if(array.length == 0) {
	                	errorLabel.setText("Plug in the arduino before submitting");
	                	return;
	                }
	                
	        		SerialPort commPort = null;
	        		// Loops through all possible to find which one the Nano 33 BLE is on -- If not detected, quits
	        		for(int i = 0; i < array.length; i++) {
	        			if(array[i].toString().equals("Nano 33 BLE")) {
	        				commPort = array[i];
	        			}
	        		}
	        		if(commPort == null) {
	        			errorLabel.setText("Nano 33 BLE not detected");
	        			return;
	        		}
	        		
	        		final SerialPort nanoPort = commPort; // redefined here for the Shutdown Hook
	        		nanoPort.setComPortParameters(9600, Byte.SIZE, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	        		nanoPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);
	        		boolean hasOpened = nanoPort.openPort();
	        		if(!hasOpened) {
	        			errorLabel.setText("Failed to open Serial Port");
	        			return;
	        		}
	        		awaitingResponse = true;
	        		secondResponse = false;
	        		Runtime.getRuntime().addShutdownHook(new Thread(() -> {nanoPort.closePort(); }));
	        		DataListener dataListener = new DataListener(secondResponse, awaitingResponse, RFID, fingerPrint, errorLabel);
	        		nanoPort.addDataListener(dataListener);
	        		
	        		// waiting for data
	        		errorLabel.setText("");
        			messageLabel.setText("Arduino connected. Awaiting RFID");
        			
        			// messageLabel.updateUI();
        			buffer[0] = 48+RFIDMODE;
	        		System.out.println("RFIDMODE " + nanoPort.writeBytes(buffer, 1));
	        		while(nanoPort.bytesAwaitingWrite() > 0) {
	        			System.out.println("Waiting for buffer");
	        		}
	        		long timeStart = System.currentTimeMillis();
	        		System.out.println("AwaitingResponse = " + awaitingResponse);
	        		System.out.println("dataListener.getAwaitingResponse() " + dataListener.getAwaitingResponse());
	        		while(awaitingResponse == true) {
	        			// 15 seconds
	        			if(System.currentTimeMillis() - timeStart > 5000.0) {
	    	        		RFID = dataListener.getRFID();
	        				if(!RFID.equals("")) {
	        					break;
	        				}
	        				errorLabel.setText("Took too long to respond");
	        				nanoPort.closePort();
	        				return;
	        			}
	        			//System.out.println("dataListener.getAwaitingResponse() " + dataListener.getAwaitingResponse());
	        			awaitingResponse = dataListener.getAwaitingResponse();
	        		}
    				nanoPort.closePort();
	        		RFID = dataListener.getRFID();
	        		System.out.println("attributes[2] vs RFID: " + attributes[2] + " / " + RFID);
	        		if(attributes[2].equals(RFID)) {
	        			nanoPort.openPort();
	        			secondResponse = true;
	        			awaitingResponse = true;
	        			dataListener.setSecondResponse(secondResponse);
	        			dataListener.setAwaitingResponse(awaitingResponse);
		        		messageLabel.setText("RFID Verified. Awaiting FingerPrint");
		        		// Send a message to Arduino to switch to FingerPrint mode
		        		buffer[0] = 48+FINGERPRINTMODE;
		        		System.out.println("FINGERPRINTMODE " + nanoPort.writeBytes(buffer, 1));
		        		while(nanoPort.bytesAwaitingWrite() > 0) {
		        			System.out.println("Waiting for buffer");
		        		}
		        		timeStart = System.currentTimeMillis();
		        		while(awaitingResponse == true) {
		        			// 15 seconds
		        			if(System.currentTimeMillis() - timeStart > 5000.0) {
		        				fingerPrint = dataListener.getFingerPrint();
		        				if(!fingerPrint.equals("")) {
		        					break;
		        				}
		        				errorLabel.setText("Took too long to respond");
		        				nanoPort.closePort();
		        				return;
		        			}
		        			awaitingResponse = dataListener.getAwaitingResponse();
		        		}
        				nanoPort.closePort();
		        		fingerPrint = dataListener.getFingerPrint();
		        		System.out.println("attributes[3] vs fingerPrint: " + attributes[3] + " vs " + fingerPrint);
		        		if(attributes[3].equals(fingerPrint)) {
		        			secondResponse = false;
			        		messageLabel.setText("Identity confirmed. Welcome!");
		        		} else {
		        			messageLabel.setText("FingerPrint Incorrect. Please try again");
		        			return;
		        		}
	        		} else {
		        		messageLabel.setText("RFID Incorrect. Enter Username and Password again");
		        		return;
	        		}
	            }
	        } else if (e.getSource() == signupButton) {
	        	// Check to make sure that there isn't a username with that password
	        	errorLabel.setText("");
	            String username = usernameField.getText();
	            String password = new String(passwordField.getPassword());
	            RFID = "";
	            fingerPrint = "";
	            if(username.length() < 1 || password.length() < 1) {
	            	errorLabel.setText("Provide both a username and password");
	            	return;
	            }
	            // Check if the username/Password is clean
	            // ASCII values 33 - 57, 65 - 90, 97-122
	            boolean notValid = false;
	            char[] userCharArray = username.toCharArray();
	            for(char s : userCharArray) {
	            	if( !((s >= 33 && s <= 57) || (s >= 65 && s <= 90) || (s >= 97 && s <= 122))) {
	            		notValid = true;
	            	}
	            }
	            char[] passCharArray = password.toCharArray();
	            for(char s : passCharArray) {
	            	if( !((s >= 33 && s <= 57) || (s >= 65 && s <= 90) || (s >= 97 && s <= 122))) {
	            		notValid = true;
	            	}
	            }
	            
	            if(notValid == true) {
	            	errorLabel.setText("Your Username / Password is invalid. Only use digits, special characters, or text");
	            	return;
	            }
	            
	            file = new File("src/Users.txt");
		        try {
					scanner = new Scanner(file);
				} catch (FileNotFoundException exception) {
					exception.printStackTrace();
				}
		        String currentUser = "";
		        String[] attributes = null;
		        boolean correctPassword = false;
		        while(scanner.hasNextLine()) {
		        	attributes = null; // Reset in case the same username but different password
			        currentUser = scanner.nextLine();
			        String currentName = currentUser.substring(0, currentUser.indexOf(':'));
			        if(username.equals(currentName)) {
		            	attributes = currentUser.split(":");
		            	if(password.equals(attributes[1])) {
		            		correctPassword = true;
		            		break;
		            	}
		            	continue;
			        }
		        }
		        
		        if(correctPassword == true) { 
		        	// Means there is a username with that password
		        	messageLabel.setText("Choose another username/password");
		        	return;
		        } else {
		        	// While arduino not connected, don't proceed
	        		SerialPort[] array = SerialPort.getCommPorts();
	                if(array.length == 0) {
	                	errorLabel.setText("Plug in the arduino before submitting");
	                	return;
	                }
	                
	        		SerialPort commPort = null;
	        		// Loops through all possible to find which one the Nano 33 BLE is on -- If not detected, quits
	        		for(int i = 0; i < array.length; i++) {
	        			if(array[i].toString().equals("Nano 33 BLE")) {
	        				commPort = array[i];
	        			}
	        		}
	        		if(commPort == null) {
	        			errorLabel.setText("Nano 33 BLE not detected");
	        			return;
	        		}
	        		
	        		final SerialPort nanoPort = commPort; // redefined here for the Shutdown Hook
	        		nanoPort.setComPortParameters(9600, Byte.SIZE, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	        		nanoPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);
	        		boolean hasOpened = nanoPort.openPort();
	        		if(!hasOpened) {
	        			errorLabel.setText("Failed to open Serial Port");
	        			return;
	        		}
	        		
	        		Runtime.getRuntime().addShutdownHook(new Thread(() -> {nanoPort.closePort(); }));
	        		DataListener dataListener = new DataListener(secondResponse, awaitingResponse, RFID, fingerPrint, errorLabel);
	        		nanoPort.addDataListener(dataListener);
	        		
	        		// waiting for data
	        		errorLabel.setText("");
	        		messageLabel.setText("Arduino connected. Awaiting RFID to enroll");
	        		buffer[0] = 48+SIGNUPRFID;
	        		nanoPort.writeBytes(buffer, 1);
	        		long timeStart = System.currentTimeMillis();
	        		while(awaitingResponse) {
	        			// 15 seconds
	        			if(System.currentTimeMillis() - timeStart > 15000.0) {
	        				errorLabel.setText("Took too long to respond");
	        				return;
	        			}
	        		}
	        		
	        		// RFID Acquired if we reach here
	        		// RFID can be shared between users, might change later
	        		
	        		secondResponse = true;
	        		awaitingResponse = true;
	        		messageLabel.setText("RFID Acquired. Awaiting FingerPrint");
	        		buffer[0] = 48+SIGNUPFINGERPRINT;
	        		nanoPort.writeBytes(buffer, 1);
	        		
	        		timeStart = System.currentTimeMillis();
	        		while(awaitingResponse) {
	        			// 15 seconds
	        			if(System.currentTimeMillis() - timeStart > 15000.0) {
	        				errorLabel.setText("Took too long to respond");
	        				return;
	        			}
	        		}
	        		
	        		// FingerPrint Acquired
		        	secondResponse = false;
		        	messageLabel.setText("Fingerprint Acquired. Creating user");
		        	
		        	String newUser = username + ":" + password + ":" + RFID + ":" + fingerPrint + ",";
		        	
		        	try {
		        		BufferedWriter write = new BufferedWriter(new FileWriter(file, true));
		        		write.write(newUser);
		        		messageLabel.setText("User added to file");
		        	} catch (IOException exception) {
		        		exception.printStackTrace();
		        	}
		        	
		        }
		        
	        }
	    }

	    public static void main(String[] args) {
	        SwingUtilities.invokeLater(Main::new);
	    }
	    
}

class DataListener implements SerialPortDataListener {
	
	private boolean secondResponse;
	private boolean awaitingResponse;
	private String RFID;
	private String fingerPrint;
    private JLabel errorLabel;
    
    public DataListener(boolean secondResponse, boolean awaitingResponse, 
    		String RFID, String fingerPrint, JLabel errorLabel) {
    	this.secondResponse = secondResponse;
    	this.awaitingResponse = awaitingResponse;
    	this.RFID = RFID;
    	this.fingerPrint = fingerPrint;
    	this.errorLabel = errorLabel;
    }

	@Override
	public int getListeningEvents() {
		 return SerialPort.LISTENING_EVENT_DATA_RECEIVED | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
	}

	@Override
	public void serialEvent(SerialPortEvent serialPortEvent) {
		if(serialPortEvent.getEventType() == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
			byte[] bytes = serialPortEvent.getReceivedData(); // Data Received in Bytes			
			if(secondResponse == false) {
				if (bytes.length > 2) {
					// System.out.print(bytes.length + " ");
    				String str = new String(bytes, StandardCharsets.UTF_8);
    				if(str.equals("Starting the RFID Reader...")) {
    					return;
    				}
    				str = str.trim();
    				if(RFID.equals("")) {
        				System.out.println("RFID: " + str);
    				}
    				RFID = str;
    				awaitingResponse = false;
				}
			} else {
				if (bytes.length > 2) {
					String str = new String(bytes, StandardCharsets.UTF_8);
					str = str.trim();
					// System.out.println(str);
					if(RFID.equals(str)) {
						return;
					}
					if(fingerPrint.equals("")) {
						System.out.println("FingerPrint: " + str);
					}
					fingerPrint = str;
					awaitingResponse = false;
				}
			}
		} else if (serialPortEvent.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
			// Nano is disconnected. 
			errorLabel.setText("Nano is disconnected");
		}
		
	}
	
	public void setAwaitingResponse(boolean awaitingResponse) {
		this.awaitingResponse = awaitingResponse;
	}
	public void setSecondResponse(boolean secondResponse) {
		this.secondResponse = secondResponse;
	}
	public void setRFID(String RFID) {
		this.RFID = RFID;
	}
	public void setFingerPrint(String fingerPrint) {
		this.fingerPrint = fingerPrint;
	}
 	
	public boolean getAwaitingResponse() {
		return awaitingResponse;
	}
	public boolean getSecondResponse() {
		return secondResponse;
	}
	public String getRFID() {
		return RFID;
	}
	public String getFingerPrint() {
		return fingerPrint;
	}
	
}


