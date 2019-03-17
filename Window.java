import com.fazecast.jSerialComm.SerialPort;
import com.github.strikerx3.jxinput.XInputDevice14;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;

public class Window extends JFrame {
	private boolean closeRequested = false;
	private SerialPort serialPort;
	private boolean vibrate = false;
	private int waitTime = 5;
	private int controller = 0;

	private XInputDevice14 device;

	private JLabel error, error2, sliderDes, sliderDes2, controllerInfo, vibrationDes, comPortDes, portDes, contentDes, allPorts, baudRateDes;
	private JPanel content;
	private JSlider slider, slider2;
	private JButton toggleVibration, update, switchPort;
	private JTextField comPort, baudRate;

	public Window() {
		super("Controller -> Bluetooth");
		this.setSize(400, 420);
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closeRequested = true;
			}
		});

		content = new JPanel(null);

		this.setContentPane(content);

		error = new JLabel("");
		error.setForeground(Color.RED);
		addContent(error, 0);

		error2 = new JLabel("");
		error2.setForeground(Color.RED);
		addContent(error2, 1);

		sliderDes = new JLabel("Wartezeit zwischen Controller Updates (in ms): " + waitTime);
		addContent(sliderDes, 3);

		slider = new JSlider(0, 200);
		slider.setValue(0);
		addContent(slider, 4);

		slider.addChangeListener(new ChangeListener() {
									 @Override
									 public void stateChanged(ChangeEvent e) {
										 waitTime = slider.getValue();
										 sliderDes.setText("Wartezeit zwischen Controller Updates (in ms): " + waitTime);
									 }
								 }
		);

		sliderDes2 = new JLabel("Controller-ID: " + controller);
		addContent(sliderDes2, 5);

		slider2 = new JSlider(0, 15);
		slider2.setValue(0);
		addContent(slider2, 6);

		slider2.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e2) {
				controller = slider2.getValue();
				sliderDes2.setText("Controller-ID: " + controller);
				try {
					device = XInputDevice14.getDeviceFor(controller);
				} catch (Exception e) {
					printError(e.getMessage());
				}
			}
		});

		controllerInfo = new JLabel("");
		addContent(controllerInfo, 7);

		vibrationDes = new JLabel("Controller-Vibration: " + (vibrate ? "Ein" : "Aus"));
		addContent(vibrationDes, 8);
		toggleVibration = new JButton("Controller Vibration umschalten");
		addContent(toggleVibration, 9);

		toggleVibration.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				vibrate = !vibrate;
				vibrationDes.setText("Controller-Vibration: " + (vibrate ? "Ein" : "Aus"));
			}
		});

		comPortDes = new JLabel("Serieller Port:");
		addContent(comPortDes, 11);
		comPort = new JTextField();
		addContent(comPort, 12);

		switchPort = new JButton("Port auswählen");
		addContent(switchPort, 15);

		switchPort.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (serialPort.isOpen()) serialPort.closePort();
				serialPort = SerialPort.getCommPort(comPort.getText());
				if (!serialPort.isOpen()) serialPort.openPort();

				int baud = 9600;
				try {
					baud = Integer.valueOf(baudRate.getText());
				} catch (Exception e2) {
					baud = 9600;
				}
				serialPort.setBaudRate(baud);
			}
		});

		portDes = new JLabel("");
		addContent(portDes, 16);

		baudRateDes = new JLabel("Baudrate:");
		addContent(baudRateDes, 13);

		baudRate = new JTextField("9600");
		addContent(baudRate, 14);


		contentDes = new JLabel("");
		addContent(contentDes, 17);

		StringBuilder ports = new StringBuilder("Verfügbare Ports: ");
		for (SerialPort port: SerialPort.getCommPorts()) {
			ports.append(port.getSystemPortName());
			ports.append(" ");
		}
		allPorts = new JLabel(ports.toString());
		addContent(allPorts, 19);

		update = new JButton("Ports suchen");
		addContent(update, 20);
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				StringBuilder ports = new StringBuilder("Verfügbare Ports: ");
				for (SerialPort port: SerialPort.getCommPorts()) {
					ports.append(port.getSystemPortName());
					ports.append(" ");
				}
				allPorts.setText(ports.toString());
			}
		});

		this.setVisible(true);
		Insets i = getInsets();
		this.setSize(400 + i.left + i.right, 420 + i.top + i.bottom);

		if (!XInputDevice14.isAvailable()) {
			printError("XInputDevice not available");
		}

		try {
			device = XInputDevice14.getDeviceFor(controller);
		} catch (Exception e) {
			printError(e.getMessage());
		}

		serialPort = SerialPort.getCommPort("");
	}

	private void addContent(JComponent component, int id) {
		content.add(component);
		component.setBounds(0, 20*id, 400, 20);
	}

	public void loop() {

		tripleVibrate();

		while (!closeRequested) {
			try {
				Thread.sleep(waitTime);
			} catch (Exception e) {	}

			if (device.poll()) {
				if (device.getComponents().getButtons().a && vibrate) {
					device.setVibration(35665, 35665);
				} else {
					device.setVibration(0, 0);
				}


				float dx = device.getComponents().getAxes().lx;
				float dy = device.getComponents().getAxes().ly;

				double deg = Math.atan2(dx, dy);
				if (deg <  0) {
					deg += 2*Math.PI;
				}

				int a = bounds((int) (Math.toDegrees(deg) / 2), 0, 179);
				int pow = bounds((int) (Math.min(1, Math.sqrt(dx*dx+dy*dy)) * 255), 0, 254);
				int shot = device.getComponents().getButtons().a ? 1 : 2;

				byte[] message = new byte[] {(byte) 0xff, (byte)((a+pow+shot) % 254 + 1), (byte)a, (byte)pow, (byte)shot};

				controllerInfo.setText(String.format("Winkel: %d, Power: %d, Schuss: %d", a, pow, shot));

				error.setText("");
				portDes.setText(String.format("Port: %s, Rate: %d, Verfügbar: %b", serialPort.getSystemPortName(), serialPort.getBaudRate(), serialPort.getOutputStream() != null));

				if (serialPort.getOutputStream() == null) {
					error2.setText("Serieller Port nicht erreichbar");
					continue;
				}

				try {
					contentDes.setText(String.format("Gesendete Daten: %d, %d, %d, %d, %d", 0xff & message[0], 0xff & message[1], 0xff & message[2], 0xff & message[3], 0xff & message[4]));
					serialPort.getOutputStream().write(message);
					serialPort.getOutputStream().flush();
				} catch (Exception e) {
					error2.setText("Fehler beim senden der Daten");
					continue;
				}
				error2.setText("");
			} else {
				printError("Kein controller verbunden");
			}
		}
		serialPort.closePort();

	}

	private int bounds(int val, int min, int max) {
		if (val < min) return min;
		if (val > max) return max;
		return val;
	}

	private void tripleVibrate() {
		device.setVibration(35665, 35665);
		try {
			Thread.sleep(125);
		} catch (Exception e) {}
		device.setVibration(0, 0);
		try {
			Thread.sleep(125);
		} catch (Exception e) {}
		device.setVibration(35665, 35665);
		try {
			Thread.sleep(125);
		} catch (Exception e) {}
		device.setVibration(0, 0);
		try {
			Thread.sleep(125);
		} catch (Exception e) {}
		device.setVibration(35665, 35665);
		try {
			Thread.sleep(125);
		} catch (Exception e) {}
		device.setVibration(0, 0);
	}

	private void printError(String message) {
		error.setText(message);
	}

	public static void main(String[] args) {
		Window window = new Window();
		window.loop();
	}
}
