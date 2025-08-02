package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import client.SolarClient;
import solar.protos.GetDailyYieldResponse;
import solar.protos.RealTimeOutput;
import solar.protos.TradeRequest;
import solar.protos.TradeResponse;
import client.ThermostatClient;
import thermostat.protos.TemperatureReading;

public class EnergyDashboardGUI {
	
	private JTextArea txtAreaThermo;
	
	//Launch GUI on run
	public static void main(String[] args) {
		//Start the thermostat server
		new Thread(() -> {
			try {
				server.ThermostatServer.main(new String[]{});
			}catch(Exception e) {
				e.printStackTrace();
			}
		}, "grpc-server").start();
		
		//Start the solar panel server
		new Thread(() -> {
			try {
				server.SolarServer.main(new String[]{});
			}catch(Exception e) {
				e.printStackTrace();
			}
		}, "grpc-server").start();
		
		//Launch GUI
		SwingUtilities.invokeLater(EnergyDashboardGUI::new);
	}
	
	//constructs the GUI
	public EnergyDashboardGUI() {
		//Build swing UI
		initUI();
	}
	
	//Sets up the JFrame layout
	private void initUI() {
		JFrame frame = new JFrame("Energy Dashboard");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());
		
		JTabbedPane tabs = new JTabbedPane();
		
		//---- Thermostat Tab ----
		//create a panel with three sections for each RPC
		JPanel thermoPanel = new JPanel(new BorderLayout());
		JPanel thermoControls = new JPanel(new GridLayout(3,1));
		
		//Set Target Temperature
		JPanel setPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		setPanel.add(new JLabel("Target (°C):"));
		JTextField txtTarget = new JTextField(5);
		setPanel.add(txtTarget);
		JButton btnSetTarget = new JButton("Set Target");
		setPanel.add(btnSetTarget);
		thermoControls.add(setPanel);
		
		//Stream Temperature History
		JPanel histPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		histPanel.add(new JLabel("History Start (yyyy-mm-ddThh:mm:ss):"));
		JTextField txtHistStart = new JTextField(16);
		histPanel.add(txtHistStart);
		histPanel.add(new JLabel("History End (yyyy-mm-ddThh:mm:ss)"));
		JTextField txtHistEnd = new JTextField(16);
		histPanel.add(txtHistEnd);
		JButton btnGetHistory = new JButton("Get History");
		histPanel.add(btnGetHistory);
		thermoControls.add(histPanel);
		
		//Compute Average Temperature
		JPanel avgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		avgPanel.add(new JLabel("Avg Start (yyyy-mm-ddThh:mm:ss):"));
		JTextField txtAvgStart = new JTextField(16);
        avgPanel.add(txtAvgStart);
        avgPanel.add(new JLabel("Avg End (yyyy-mm-ddThh:mm:ss):"));
        JTextField txtAvgEnd = new JTextField(16);
        avgPanel.add(txtAvgEnd);
        JButton btnGetAverage = new JButton("Get Average");
        avgPanel.add(btnGetAverage);
        thermoControls.add(avgPanel);
        
        thermoPanel.add(thermoControls, BorderLayout.NORTH);
        
        // Center area for output log
        txtAreaThermo = new JTextArea(15, 50);
        txtAreaThermo.setEditable(false);
        thermoPanel.add(new JScrollPane(txtAreaThermo), BorderLayout.CENTER);
        
        tabs.addTab("Thermostat", thermoPanel);
        frame.add(tabs, BorderLayout.CENTER);
        
        //---- Solar Tab ----
        JPanel solarPanel = new JPanel(new BorderLayout());
        JPanel solarControls = new JPanel(new GridLayout(3,1));
        
        //Get Daily Yield
        JPanel dailyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dailyPanel.add(new JLabel("Date (YYYY-MM-DD):"));
        JTextField txtSolarDate = new JTextField(10);
        dailyPanel.add(txtSolarDate);
        JButton btnDailyYield = new JButton("Get Daily Yield");
        dailyPanel.add(btnDailyYield);
        solarControls.add(dailyPanel);
        
        // Stream real time output
        JPanel streamPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        streamPanel.add(new JLabel("Samples:"));
        JTextField txtSolarCount = new JTextField(5);
        streamPanel.add(txtSolarCount);
        JButton btnStreamOutput = new JButton("Stream Output");
        streamPanel.add(btnStreamOutput);
        solarControls.add(streamPanel);
        
        // Energy trade negotiation
        JPanel tradePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tradePanel.add(new JLabel("Price: "));
        JTextField txtTradePrice = new JTextField(5);
        tradePanel.add(txtTradePrice);
        tradePanel.add(new JLabel("Qty:"));
        JTextField txtTradeQty = new JTextField(5);
        tradePanel.add(txtTradeQty);
        JButton btnTrade = new JButton("Negotiate Trade");
        tradePanel.add(btnTrade);
        solarControls.add(tradePanel);
        
        solarPanel.add(solarControls, BorderLayout.NORTH);
        
        JTextArea txtAreaSolar = new JTextArea(15, 50);
        txtAreaSolar.setEditable(false);;
        solarPanel.add(new JScrollPane(txtAreaSolar), BorderLayout.CENTER);
        
        tabs.addTab("Solar", solarPanel);
        
        //---- Lighting Tab ----
        JPanel lightingPanel = new JPanel(new BorderLayout());
        JLabel lightPlaceholder = new JLabel("Lighting controls pending implementation", SwingConstants.CENTER);
        lightingPanel.add(lightPlaceholder, BorderLayout.CENTER);
        
        tabs.addTab("Lighting", lightingPanel);

        frame.add(tabs, BorderLayout.CENTER);
        
        //Action listeners
        //---- Thermostat Listener ----
        btnSetTarget.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			double t = Double.parseDouble(txtTarget.getText());
        			boolean ok = ThermostatClient.setTarget(t);
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Set target → success=" + ok + "\n"));
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });
        
        btnGetHistory.addActionListener(e -> {
        	new Thread(() -> {
        		try {
        			LocalDateTime ldtStart = LocalDateTime.parse(txtHistStart.getText());
        			LocalDateTime ldtEnd = LocalDateTime.parse(txtHistEnd.getText());
        			
        			long s1 = ldtStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        			long e1 = ldtEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        			List<TemperatureReading> hist = ThermostatClient.getHistory(s1, e1);
        			SwingUtilities.invokeLater(() -> {
        				txtAreaThermo.append("--- History ---\n");
        				for (TemperatureReading r : hist) {
        					txtAreaThermo.append(String.format("t=%d → %.2f°C\n", r.getTimestamp(), r.getTemperature()));
        				}
        			});
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });
        
        btnGetAverage.addActionListener(e -> {
        	new Thread(() -> {
        		try {
        			LocalDateTime ldtStart = LocalDateTime.parse(txtAvgStart.getText());
        			LocalDateTime ldtEnd = LocalDateTime.parse(txtAvgEnd.getText());
        			
        			long s1 = ldtStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        			long e1 = ldtEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        			double avg = ThermostatClient.getAverage(s1, e1);
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append(String.format("Average=%.2f°C\n", avg)));
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });
        
        //---- Solar Listener ----
        btnDailyYield.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			GetDailyYieldResponse resp = SolarClient.getDailyYield(txtSolarDate.getText());
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append(String.format("Yield: %.2f kWh, Peak: %.2f kW\n", resp.getYieldKw(), resp.getPeak())));
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });
        
        btnStreamOutput.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			int count = Integer.parseInt(txtSolarCount.getText());
        			Iterator<RealTimeOutput> it = SolarClient.streamRealTimeOutput();
        			List<RealTimeOutput> outputs = new ArrayList<>();
        			while (it.hasNext() && outputs.size() < count) {
        				outputs.add(it.next());
        			}
        			SwingUtilities.invokeLater(() -> {
        				txtAreaSolar.append("Real Time Output\n");
        				outputs.forEach(o -> txtAreaSolar.append(String.format("%s → %.2f kW\n", o.getTimestamp(), o.getCurrentKw())));
        			});
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });
        
        btnTrade.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			double price = Double.parseDouble(txtTradePrice.getText());
        			double qty = Double.parseDouble(txtTradeQty.getText());
        					List<TradeRequest> reqs = Arrays.asList(TradeRequest.newBuilder().setPrice(price).setQuantity(qty).build());
        					List<TradeResponse> resps = SolarClient.negotiateTrades(reqs);
        					SwingUtilities.invokeLater(() -> {
        						txtAreaSolar.append("Trade Negotiation\n");
        						resps.forEach(r -> txtAreaSolar.append(r.toString() + "\n"));
        					});
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n"));
        		}
        	}).start();
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
	
    