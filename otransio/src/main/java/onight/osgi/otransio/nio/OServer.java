package onight.osgi.otransio.nio;

import java.io.IOException;

import onight.osgi.otransio.impl.OSocketImpl;
import onight.tfw.outils.conf.PropHelper;

import org.apache.felix.ipojo.annotations.Invalidate;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OServer {

	Logger log = LoggerFactory.getLogger(OServer.class);
	private TCPNIOTransport transport;

	public int PORT = 5100;

	public void startServer(OSocketImpl listener, PropHelper params) {
		// Create a FilterChain using FilterChainBuilder
		FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
		// Add TransportFilter, which is responsible
		// for reading and writing data to the connection
		filterChainBuilder.add(new TransportFilter());

		// StringFilter is responsible for Buffer <-> String conversion
		filterChainBuilder.add(new OTransFilter());

		// 登录协议
		// filterChainBuilder.add(new AuthFilter());

		// EchoFilter is responsible for echoing received messages
		SessionFilter sf=new SessionFilter(listener, params);
		filterChainBuilder.add(sf);

		// Create TCP transport
		transport = TCPNIOTransportBuilder.newInstance().build();
		transport.setProcessor(filterChainBuilder.build());
		ThreadPoolConfig ktpc=ThreadPoolConfig.defaultConfig();
		ktpc.setCorePoolSize(params.get("otrans.kernel.core", 10)).setMaxPoolSize(params.get("otrans.kernel.max", 100));
		transport.setKernelThreadPoolConfig(ktpc);

		ThreadPoolConfig wtpc=ThreadPoolConfig.defaultConfig();
		wtpc.setCorePoolSize(params.get("otrans.worker.core", 10)).setMaxPoolSize(params.get("otrans.worker.max", 100));
		transport.setWorkerThreadPoolConfig(wtpc);

		transport.setTcpNoDelay(true);
		try {
			// binding transport to start listen on certain host and port
			int oport = params.get("otrans.port", PORT);
			log.debug("port=" + oport);
			transport.bind(params.get("otrans.addr", "0.0.0.0"), oport);
			log.info("socket服务开启成功:" + oport);
			transport.start();
			
		} catch (IOException e) {
			log.error("socket服务开启失败:", e);

			e.printStackTrace();
		} finally {

		}
	}

	@Invalidate
	public void stop() {
		try {
			transport.shutdownNow();
		} catch (IOException e) {
			e.printStackTrace();
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			log.info("socket服务关闭");
		}

	}

}
