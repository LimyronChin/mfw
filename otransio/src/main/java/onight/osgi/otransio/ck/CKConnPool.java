package onight.osgi.otransio.ck;

import java.io.IOException;
import java.util.Iterator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.nio.OClient;
import onight.osgi.otransio.sm.MSessionSets;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.beans.ExtHeader;
import onight.tfw.otransio.api.beans.FixHeader;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.outils.pool.ReusefulLoopPool;

import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ICloseType;

@Data
@Slf4j
public class CKConnPool extends ReusefulLoopPool<Connection> {
	OClient client;
	String ip;
	int port;
	int core;
	int max;
	boolean stop = false;

	MSessionSets mss;

	public CKConnPool(OClient client, String ip, int port, int core, int max, MSessionSets mss) {
		super();
		this.client = client;
		this.ip = ip;
		this.port = port;
		this.core = core;
		this.max = max;
		this.mss = mss;
	}

	public synchronized Connection createOneConnection() {
		return createOneConnection(0);
	}

	public synchronized Connection createOneConnection(int maxtries) {

		if (size() < max && maxtries < 5) {
			try {
				Connection conn = client.getConnection(ip, port);
				if (conn != null) {
					conn.addCloseListener(new CloseListener<Closeable, ICloseType>() {
						@Override
						public void onClosed(Closeable closeable, ICloseType type) throws IOException {
							log.info("CheckHealth remove Connection!:" + closeable);
							if (closeable instanceof Connection) {
								removeObject((Connection) closeable);
							}
						}
					});
					FramePacket pack=mss.getLocalModulesPacket();
					conn.write(pack);
					this.addObject(conn);
				} else {
					createOneConnection(maxtries + 1);
				}
				return conn;
			} catch (Exception e) {
				log.warn("error in create out conn:" + ip + ",port=" + port, e);
			}
		}
		return null;
	}

	public void broadcastMessage(Object msg) {
		Iterator<Connection> it = this.iterator();
		while (it.hasNext()) {
			it.next().write(msg);
		}
	}
}
