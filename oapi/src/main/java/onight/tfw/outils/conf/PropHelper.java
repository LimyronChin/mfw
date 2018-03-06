package onight.tfw.outils.conf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

/**
 * 配置信息帮助类
 * 
 * @author brew
 * 
 */
@Slf4j
public class PropHelper {

	private File configFile;

	BundleContext context;

	public PropHelper(BundleContext context, String propfile) {

		if (context == null) {
			context = BundleReference.class.cast(PropHelper.class.getClassLoader()).getBundle().getBundleContext();
		}
		this.context = context;
		String config = context.getProperty(propfile);
		if (config == null) {
			config = "./conf/ofw.properties";
			if (!new File(config).exists()) {
				config = "../conf/ofw.properties";
			}
		}
		configFile = new File(config);
		init();
		for (String subdir : get("include", "").split(",")) {
			if (StringUtils.isBlank(subdir))
				continue;
			File dir = new File(configFile.getParent()+File.separator+subdir);
			if (dir.isDirectory()) {
				for (File f : dir.listFiles(new FilenameFilter() {

					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith("properties");
					}
				})) {
					addFileProps(f);
				}
			}else if(dir.isFile()&&dir.getName().endsWith("properties")){
				addFileProps(dir);
			}
		}
		log.debug("configFile::" + config + "::FF==" + configFile.exists());
	}

	public PropHelper(BundleContext context) {
		this(context, "ofwConf");
	}

	Properties local_props = new Properties();

	public void init() {
		addFileProps(configFile);
	}

	public void addFileProps(File file) {
		if (file.exists()) {
			Properties propsConf = new Properties();
			try {
				InputStream in = new BufferedInputStream(new FileInputStream(file));
				propsConf.load(in);
				in.close();
			} catch (Exception e) {

			}
			local_props.putAll(propsConf);

			if (file == configFile)
			{// try ext
				Properties extpropsConf = new Properties();
				String extconfig = "./conf/ofwext.properties";
				if (!new File(extconfig).exists()) {
					extconfig = "../conf/ofwext.properties";
				}

				if (!new File(extconfig).exists()) {
					extconfig = configFile.getParent() + File.separator + "ofwext.properties";
				}
				File extconf = new File(extconfig);
				if (extconf.exists()) {
					try {
						InputStream in = new BufferedInputStream(new FileInputStream(extconf));
						extpropsConf.load(in);
						in.close();
					} catch (Exception e) {

					}
					local_props.putAll(extpropsConf);
				}
			}
		}
	}

	public String get(String key, String defaultv) {
		Object v = local_props.get(key);
		if (v == null) {
			return defaultv;
		}
		return v.toString();
	}

	public static interface IFinder {
		public void onMatch(String key, String v);
	}

	public void findMatch(String kv, IFinder match) {
		for (Entry<Object, Object> entry : local_props.entrySet()) {
			String key = (String) entry.getKey();
			String v = (String) entry.getValue();
			if (key.matches(kv)) {
				match.onMatch(key, v);
			}
		}
	}

	public int get(String key, int defaultv) {
		Object v = local_props.get(key);
		if (v == null) {
			return defaultv;
		}
		try {
			return Integer.parseInt(v.toString());
		} catch (NumberFormatException e) {
		}
		return defaultv;
	}

}
