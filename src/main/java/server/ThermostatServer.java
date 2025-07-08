package server;

import java.io.IOException;
import java.util.Iterator;
import java.net.InetAddress;
import java.util.logging.Logger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;



public class ThermostatServer extends ThermostatServiceImpl{
	
	
	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		ThermostatServer thermostatserver = new ThermostatServer();
		
		int port = 50051;
		Server server = ServerBuilder
				.forPort(port)
				.addService(thermostatserver)
				.build()
				.start();
		
		logger.info("Thermostat server started, listening on " + port);
		
		
		
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		
		ServiceInfo serviceInfo = ServiceInfo.create("_thermostat._grpc._tcp.local.", "ThermostatService", port, "Thermostat Server will give you the current temperature");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Thermostat Server loop");
		
		server.awaitTermination();
		
	}
	
}