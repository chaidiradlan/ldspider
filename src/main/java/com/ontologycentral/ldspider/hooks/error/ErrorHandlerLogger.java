package com.ontologycentral.ldspider.hooks.error;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.http.Header;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.parser.Callback;
import org.semanticweb.yars.nx.parser.NxParser;

import com.ontologycentral.ldspider.CrawlerConstants;

public class ErrorHandlerLogger implements ErrorHandler {
	Logger _log = Logger.getLogger(this.getClass().getName());
	
	public static int RESOLUTION = 100;

	List<ObjectThrowable> _errors;

	protected final Map<Integer, Integer> _status;
	protected final Map<Integer, Integer> _rostatus;

	protected final Map<String, Integer> _cache;
	protected final Map<String, Integer> _rocache;

	protected final Map<String, Integer> _type;
	protected final Map<String, Integer> _rotype;

	protected final Map<Integer, Integer> _time;
	protected final Map<Integer, Integer> _rotime;
	
	Appendable _logger = null;
	
	Callback _redirects = null;
	
	boolean _summary;
	
	long _lookups;
	
	final String lineSeparator = System.getProperty("line.separator");

	SimpleDateFormat _df;

	public ErrorHandlerLogger(Appendable out, Callback redirects) {
		this(out, redirects, false);
	}
	
	/**
	 * logging redirects to file
	 */
	public ErrorHandlerLogger(Appendable out, Callback redirects, boolean summary) {
		_logger = out;
		
		_summary = summary;

		_redirects = redirects;
		
		_errors = Collections.synchronizedList(new ArrayList<ObjectThrowable>());
		
		_status = Collections.synchronizedMap(new TreeMap<Integer, Integer>());
		_rostatus = Collections.synchronizedMap(new TreeMap<Integer, Integer>());

		_cache = Collections.synchronizedMap(new TreeMap<String, Integer>());
		_rocache = Collections.synchronizedMap(new TreeMap<String, Integer>());
		
		_type = Collections.synchronizedMap(new TreeMap<String, Integer>());
		_rotype = Collections.synchronizedMap(new TreeMap<String, Integer>());
		
		_time = Collections.synchronizedMap(new TreeMap<Integer, Integer>());
		_rotime = Collections.synchronizedMap(new TreeMap<Integer, Integer>());
		
		_lookups = 0;

		_df = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.US);
	}

	public void handleError(Throwable e) {
		handleError(null, e);
	}

	public void handleError(URI u, Throwable e) {
		_log.info("ERROR: " + e.getMessage() + ": " + u);

		if (e.getMessage() == null) {
			e.printStackTrace();
		}

		ObjectThrowable ut = new ObjectThrowable(u, e);

		_errors.add(ut);
	}

	public void handleStatus(URI u, int status, Header[] headers, long duration, long contentLength) {
		String type = null;
		String cache = "MISS";
		
		if (headers != null) {
			for (Header h : headers) {
				String name = h.getName().toLowerCase();
				String value = h.getValue();

				if ("content-type".equals(name)) {
					type = value;
					if (type.indexOf(';') > 0) {
						type = type.substring(0, type.indexOf(';'));
					}
				} else if ("x-cache".equals(name)) {
					if (value.indexOf(' ') > 0) {
						cache = value.substring(0, value.indexOf(' '));
					}
				}
			}
		}
		
		if ("/robots.txt".equals(u.getPath())) {
			increment(_rostatus, status);
			increment(_rocache, cache);
			increment(_rotype, type);
			
			int tbracket = (int)((float)duration/(float)RESOLUTION);
			increment(_rotime, tbracket);
		} else {
			increment(_status, status);
			increment(_cache, cache);
			increment(_type, type);

			int tbracket = (int)((float)duration/(float)RESOLUTION);
			increment(_time, tbracket);
			
			if (status != CrawlerConstants.SKIP_SUFFIX && status != CrawlerConstants.SKIP_ROBOTS) {
				_lookups++;
			}
		}

		if (_logger != null) {
			StringBuilder sb = new StringBuilder();

			// common.log: 127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif HTTP/1.0" 200 2326
			// native.log: time elapsed remotehost code/status bytes method URL rfc931 peerstatus/peerhost type
			// 1262626658.480     13 127.0.0.1 TCP_HIT/200 594 GET http://umbrich.net/robots.txt - NONE/- text/plain
			//Common Logfile Format
			sb.append("127.0.0.1 "); // host
			sb.append("- "); // RFC 1413 identity
			sb.append("- "); // userid of the person (HTTP authentication)
			sb.append("["); // date
			sb.append(_df.format(new Date()));
			sb.append("] ");
			sb.append("\"GET ");
			sb.append(u);
			sb.append("\" ");
			sb.append(status);
			sb.append(" ");
			sb.append(contentLength);

			synchronized(this) {
				try {
					_logger.append(sb);
					_logger.append(lineSeparator);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} 
	}
	
	<T> void increment(Map<T, Integer> m, T key) {
		if (_summary) {
			if (key != null) {
				Integer count = (Integer)m.get(key);
				if (count == null) {
					m.put(key, 1);
				} else {
					count++;
					m.put(key, count);
				}
			}
		}
	}

	public String toString() {
		if (_summary)
			return summaryToString();
		else
			return "";
	}
	
	public String summaryToString() {
		StringBuffer sb = new StringBuffer();

		sb.append("robots.txt lookups\n");
		sb.append(toStringBuffer(_rostatus));
		sb.append("\nrobots.txt caching\n");
		sb.append(toStringBuffer(_rocache));
		sb.append("\nrobots.txt content types\n");
		sb.append(toStringBuffer(_rotype));

		sb.append("\nlookup time\n");

		for (Map.Entry<Integer, Integer> en : _rotime.entrySet()) {
			int start = en.getKey() * RESOLUTION;
			sb.append(start + "-" + (start+(RESOLUTION-1)) + ": " + en.getValue() + "\n");
		}

		sb.append("\nlookups\n");
		sb.append(toStringBuffer(_status));
		sb.append("\ncaching\n");
		sb.append(toStringBuffer(_cache));
		sb.append("\ncontent types\n");
		sb.append(toStringBuffer(_type));

		sb.append("\nlookup time \n");

		for (Map.Entry<Integer, Integer> en : _time.entrySet()) {
			int start = en.getKey() * RESOLUTION;
			sb.append(start + "-" + (start+(RESOLUTION-1)) + ": " + en.getValue() + "\n");
		}
		
		sb.append("\n");
		
		return sb.toString();
	}
	
	public StringBuffer toStringBuffer(Map<? extends Object, Integer> map) {
		StringBuffer sb = new StringBuffer();
		
		int sum = 0;
		for (Map.Entry<? extends Object, Integer> en : map.entrySet()) {
			sb.append(en.getKey() + ": " + en.getValue() + "\n");
			sum += (Integer)en.getValue();
		}

		sb.append("total: ");
		sb.append(sum);
		sb.append("\n");
		
		return sb;
	}
	
	public void close() {
		if(_logger != null && _logger instanceof Closeable) {
			try {
				((Closeable)_logger).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void handleRedirect(URI from, URI to, int status) {
		if (_redirects != null) {
			Node[] nx = new Node[2];

			nx[0] = new Resource(NxParser.escapeForNx(from.toString()));
			try {
				nx[1] = new Resource(NxParser.escapeForNx(new URI(to.getScheme(), to.getAuthority(), to.getPath(), to.getQuery(), to.getFragment()).toString()));
			} catch (URISyntaxException e) {
				_log.info("problems with " + to);
				nx[1] = new Resource(NxParser.escapeForNx(to.toString()));
			}

			_redirects.processStatement(nx);		
		}
	}

	public Iterator<ObjectThrowable> iterator() {
		return _errors.iterator();
	}

	/**
	 * return only "real" lookups, no robots.txt lookups and no filters w/o lookups
	 */
	public long lookups() {
		return _lookups;
		
//		long size = 0;
//		for (Integer status : _status.keySet()) {
//			if (status != CrawlerConstants.SKIP_SUFFIX && status != CrawlerConstants.SKIP_ROBOTS) {
//				size += _status.get(status);
//			}
//		}
//
//		return size;
	}

	public void handleLink(Node from, Node to) {
		// TODO Auto-generated method stub
		
	}

	public void handleNextRound() {
		// TODO Auto-generated method stub
		
	}
}
