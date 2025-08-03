/*
 * EnergyDashboardGUI.java
 *
 * Swing-based desktop application for a smart home energy system. 
 * Launches Thermostat, Solar, and Lighting gRPC servers locally.
 * Provides a tabbed user interface for interacting with each service. 
 *
 */

package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import javax.swing.SwingUtilities;
import client.SolarClient;
import solar.protos.GetDailyYieldResponse;
import solar.protos.RealTimeOutput;
import solar.protos.TradeRequest;
import solar.protos.TradeResponse;
import client.ThermostatClient;
import thermostat.protos.TemperatureReading;
import lighting.protos.GetCurrentBrightnessResponse;
import lighting.protos.AmbientLightReading;
import lighting.protos.LightUsageStat;
import lighting.protos.AdjustBrightnessRequest;
import lighting.protos.AdjustBrightnessResponse;
import client.LightingClient;

public class EnergyDashboardGUI {
	
	//Starts each server the launches the GUI
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
		
		//Start the lighting server
		new Thread(() -> {
			try {
				server.LightingServer.main(new String[]{});
			}catch(Exception e) {
				e.printStackTrace();
			}
		}, "grpc-server").start();
		
		//Launch GUI
		SwingUtilities.invokeLater(EnergyDashboardGUI::new);
	}
	
	//Constructs the GUI
	public EnergyDashboardGUI() {
		//Build swing UI
		initUI();
	}
	
	//Sets up the JFrame layout
	private void initUI() {
		
		//Create main application window
		JFrame frame = new JFrame("Energy Dashboard");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());
		
		//Tabbed pane to switch between services
		JTabbedPane tabs = new JTabbedPane();
		
		//---- Thermostat Tab ----
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
        
        //Add controls to top of thermostat tab
        thermoPanel.add(thermoControls, BorderLayout.NORTH);
        
        //Center area for output log
        JTextArea txtAreaThermo = new JTextArea(15, 50);
        txtAreaThermo.setEditable(false);
        thermoPanel.add(new JScrollPane(txtAreaThermo), BorderLayout.CENTER);
        tabs.addTab("Thermostat", thermoPanel);
        
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
        
        //Stream real time output
        JPanel streamPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        streamPanel.add(new JLabel("Samples:"));
        JTextField txtSolarCount = new JTextField(5);
        streamPanel.add(txtSolarCount);
        JButton btnStreamOutput = new JButton("Stream Output");
        streamPanel.add(btnStreamOutput);
        solarControls.add(streamPanel);
        
        //Energy trade negotiation
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
        
        //Add controls to top of Solar Panel tab
        solarPanel.add(solarControls, BorderLayout.NORTH);
        
        //Center area for output log
        JTextArea txtAreaSolar = new JTextArea(15, 50);
        txtAreaSolar.setEditable(false);;
        solarPanel.add(new JScrollPane(txtAreaSolar), BorderLayout.CENTER);
        tabs.addTab("Solar", solarPanel);
        
        //---- Lighting Tab ----
        JPanel lightingPanel = new JPanel(new BorderLayout());
        JPanel lightingControls = new JPanel(new GridLayout(4,1));
        
        //Current brightness
        JPanel brightnessPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        brightnessPanel.add(new JLabel("Zone:"));
        JTextField txtZone = new JTextField(10);
        brightnessPanel.add(txtZone);
        JButton btnGetBrightness = new JButton("Get Brightness");
        brightnessPanel.add(btnGetBrightness);
        lightingControls.add(brightnessPanel);
        
        //Ambient light
        JPanel ambientPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ambientPanel.add(new JLabel("Zone:"));
        JTextField txtAmbientZone = new JTextField(10);
        ambientPanel.add(txtAmbientZone);
        ambientPanel.add(new JLabel("Samples:"));
        JTextField txtAmbientCount = new JTextField(5);
        ambientPanel.add(txtAmbientCount);
        JButton btnStreamAmbient = new JButton("Stream Ambient");
        ambientPanel.add(btnStreamAmbient);
        lightingControls.add(ambientPanel);
        
        //Usage stats
        JPanel usagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        usagePanel.add(new JLabel("Duration Min:"));
        JTextField txtUsageDur = new JTextField(5);
        usagePanel.add(txtUsageDur);
        usagePanel.add(new JLabel("Avg Level:"));
        JTextField txtUsageAvg = new JTextField(5);
        usagePanel.add(txtUsageAvg);
        JButton btnUploadUsage = new JButton("Upload Usage");
        usagePanel.add(btnUploadUsage);
        lightingControls.add(usagePanel);
        
        //Adjust brightness
        JPanel adjustPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        adjustPanel.add(new JLabel("Desired Level:"));
        JTextField txtAdjustLevel = new JTextField(5);
        adjustPanel.add(txtAdjustLevel);
        adjustPanel.add(new JLabel("Occupied (true/false):"));
        JTextField txtAdjustOcc = new JTextField(5);
        adjustPanel.add(txtAdjustOcc);
        adjustPanel.add(new JLabel("Timestamp:"));
        JTextField txtAdjustTs = new JTextField(16);
        adjustPanel.add(txtAdjustTs);
        JButton btnAdjust = new JButton("Adjust Brightness");
        adjustPanel.add(btnAdjust);
        lightingControls.add(adjustPanel);
        
        //Add controls to top of Solar Panel tab
        lightingPanel.add(lightingControls, BorderLayout.NORTH);
        
        //Center area for output log
        JTextArea txtAreaLighting = new JTextArea(15,50);
        txtAreaLighting.setEditable(false);
        lightingPanel.add(new JScrollPane(txtAreaLighting), BorderLayout.CENTER);
        tabs.addTab("Lighting", lightingPanel);
        
        //Add the tabbed pane to the frame
        frame.add(tabs, BorderLayout.CENTER);


        //Action listeners
        
        //---- Thermostat Listener ----
        btnSetTarget.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			double t = Double.parseDouble(txtTarget.getText()); //Pares input
        			boolean ok = ThermostatClient.setTarget(t); //Call set target method in ThermostatClient
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Set target → success=" + ok + "\n")); //Output to GUI
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });
        
        btnGetHistory.addActionListener(e -> {
        	new Thread(() -> {
        		try {
        			LocalDateTime ldtStart = LocalDateTime.parse(txtHistStart.getText()); //Parse start
        			LocalDateTime ldtEnd = LocalDateTime.parse(txtHistEnd.getText()); //Pares end
        			long s1 = ldtStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); //Concert start to ms
        			long e1 = ldtEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); //Convert end to ms
        			List<TemperatureReading> hist = ThermostatClient.getHistory(s1, e1); //Call get history method in ThermostatClient
        			//Output to GUI
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("--- History ---\n"));
        				for (TemperatureReading r : hist) {
        					txtAreaThermo.append(String.format("t=%d → %.2f°C\n", r.getTimestamp(), r.getTemperature()));
        				}
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });
        
        btnGetAverage.addActionListener(e -> {
        	new Thread(() -> {
        		try {
        			LocalDateTime ldtStart = LocalDateTime.parse(txtAvgStart.getText()); //Parse start
        			LocalDateTime ldtEnd = LocalDateTime.parse(txtAvgEnd.getText()); //Pares end
        			long s1 = ldtStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); //Concert start to ms
        			long e1 = ldtEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); //Convert end to ms
        			double avg = ThermostatClient.getAverage(s1, e1); //Call get average method from ThermostatClient
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append(String.format("Average=%.2f°C\n", avg))); //Output to GUI
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaThermo.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });
        
        //---- Solar Listener ----
        btnDailyYield.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			GetDailyYieldResponse resp = SolarClient.getDailyYield(txtSolarDate.getText()); //Call get daily yield from SolarClient
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append(String.format("Yield: %.2f kWh, Peak: %.2f kW\n", resp.getYieldKw(), resp.getPeak()))); //Output to GUI
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });
        
        btnStreamOutput.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			int count = Integer.parseInt(txtSolarCount.getText()); //Parse count
        			Iterator<RealTimeOutput> it = SolarClient.streamRealTimeOutput(); //Call stream real time output from SolarClient
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Real Time Output\n")); //Output to GUI
        			int i = 0;
        			//While there is a next value and i is less than count
        			while (it.hasNext() && i < count) {
        				RealTimeOutput output = it.next(); //Output equals next
        				SwingUtilities.invokeLater(() -> txtAreaSolar.append(String.format("%s → %.2f kW%n", output.getTimestamp(), output.getCurrentKw()))); //Output to GUI
        	            i++;
        			}
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });
        
        btnTrade.addActionListener(evt -> {
        	new Thread(() -> {
        		try {
        			double price = Double.parseDouble(txtTradePrice.getText()); //Pares price
        			double qty = Double.parseDouble(txtTradeQty.getText()); //Parse quantity
        					List<TradeRequest> reqs = Arrays.asList(TradeRequest.newBuilder().setPrice(price).setQuantity(qty).build()); //Request 
        					List<TradeResponse> resps = SolarClient.negotiateTrades(reqs); //Response
        					//Output to GUI
        					SwingUtilities.invokeLater(() -> {
        						txtAreaSolar.append("Trade Negotiation\n");
        						resps.forEach(r -> txtAreaSolar.append(r.toString() + "\n"));
        					});
        		}catch(Exception ex) {
        			SwingUtilities.invokeLater(() -> txtAreaSolar.append("Error: " + ex.getMessage() + "\n")); //Error handling
        		}
        	}).start(); //Start background thread
        });

        // --- Lighting listeners ---
        btnGetBrightness.addActionListener(evt -> {
            new Thread(() -> {
                try {
                    GetCurrentBrightnessResponse resp = LightingClient.getCurrentBrightness(txtZone.getText().trim()); //Call method from LightingClient
                    //Output to GUI
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append(String.format("Current brightness in %s: %d%% at %s\n", txtZone.getText().trim(), resp.getLevel(), resp.getTimestamp())));
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append("Error in getBrightness: " + ex + "\n")); //Error handling
                }
            }).start(); //Start background thread
        });
        
        btnStreamAmbient.addActionListener(evt -> {
            new Thread(() -> {
                try {
                    String zone = txtAmbientZone.getText().trim(); //Get zone
                    int count = Integer.parseInt(txtAmbientCount.getText()); //Parse count
                    Iterator<AmbientLightReading> it = LightingClient.streamAmbientLightData(zone); //Call method
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append("--- Ambient Light Data ---\n")); //Output to GUI
                    int i = 0;
                    //While next exists and i is less than count
                    while (it.hasNext() && i < count) {
                    	AmbientLightReading reading = it.next(); //Output equals next
                    	//Output to GUI
                    	SwingUtilities.invokeLater(() -> txtAreaLighting.append(String.format("%s | lux=%.1f | occupied=%b%n", reading.getTimestamp(), reading.getLux(), reading.getOccupied())));
                        i++;
                    }
                    
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append("Error in ambient data: " + ex + "\n")); //Error handling
                }
            }).start();//Start background thread
        });
        
        btnUploadUsage.addActionListener(evt -> {
            new Thread(() -> {
                try {
                    int dur = Integer.parseInt(txtUsageDur.getText()); //Parse duration
                    int avg = Integer.parseInt(txtUsageAvg.getText()); //Parse average
                    List<LightUsageStat> stats = Arrays.asList(LightUsageStat.newBuilder().setDurationMin(dur).setAverageLevel(avg).build());
                    double total = LightingClient.uploadUsageStats(stats);
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append(String.format("Total energy used: %.2f kWh\n", total))); //Output to GUI
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append("Error in upload usage: " + ex + "\n")); //Error handling
                }
            }).start(); //Start background thread
        });
        
        btnAdjust.addActionListener(evt -> {
            new Thread(() -> {
                try {
                    int level = Integer.parseInt(txtAdjustLevel.getText()); //Parse level
                    boolean occ = Boolean.parseBoolean(txtAdjustOcc.getText()); //Parse occupancy
                    String ts = txtAdjustTs.getText().trim();
                    List<AdjustBrightnessRequest> reqs = Arrays.asList(AdjustBrightnessRequest.newBuilder().setDesiredLevel(level).setOccupied(occ).setTimestamp(ts).build());
                    List<AdjustBrightnessResponse> resps = LightingClient.adjustBrightness(reqs); //Call method
                    //Output to GUI
                    SwingUtilities.invokeLater(() -> {
                        txtAreaLighting.append("--- Adjust Brightness ---\n");
                        resps.forEach(r -> txtAreaLighting.append(String.format("%s | lux=%.1f\n", r.getTimestamp(), r.getLux())));
                    });
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> txtAreaLighting.append("Error in adjust brightness: " + ex + "\n")); //Error handling
                }
            }).start(); //Start background thread
        });
        
        //Frame display settings
        frame.pack(); //Set correct size
        frame.setLocationRelativeTo(null); //Center on screen
        frame.setVisible(true); //Show window
    }
}
	
    