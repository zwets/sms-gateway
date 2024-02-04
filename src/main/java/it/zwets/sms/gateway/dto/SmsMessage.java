package it.zwets.sms.gateway.dto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An <code>SmsMessage</code> has zero or more headers and a possibly empty body.
 * 
 * Header names must start with a word character (A-Za-z0-9_) and be followed
 * by zero or more word characters and/or dashes (-).
 * 
 * Header values may contain any number of characters except newlines or line
 * breaks.  Any whitespace at either end of a value is trimmed.  Setting a header
 * value to null or the empty string removes the header with a logged warning.
 * 
 * The message body may contain any number of characters, including zero.
 *
 * All methods on this class make sure that the above invariants remain true,
 * and throw the unchecked {@ SmsException} if they would be violated.
 * 
 * @author zwets
 */
public class SmsMessage implements Serializable { 

	private static final Logger LOG = LoggerFactory.getLogger(SmsMessage.class);
	private static final long serialVersionUID = 1L;
	private static final String EMPTY_BODY = "".intern();
	private static final Pattern HEADER_NAME_REGEX = Pattern.compile("^\\w[\\w-]*$");
	private static final Pattern HEADER_REGEX = Pattern.compile("^(\\w[\\w-]*)\\s*:\\s*(.*\\S?)\\s*$");
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yy-MM-dd HH:mm:ss");

	private Map<String,String> headers = new HashMap<String,String>();
	private String body = EMPTY_BODY;

	/** Construct a message with no headers and the empty body. */
	public SmsMessage() {
	}

	/** 
	 * Create SmsMessage with the given body.
	 * @param body the text body of the message, will be empty if null
	 */
	public SmsMessage(String body) {
		this.setBody(body);
	}

	/** 
	 * Create SmsMessage with a copy of the given headers and empty body.
	 * @param headers the headers to set
	 * @param body the text body of the message
	 * @throws SmsException if any header violates requirements
	 */
	public SmsMessage(Map<String,String> headers) {
		this.setHeaders(headers);
	}

	/** 
	 * Create SmsMessage with a copy of the given headers and body
	 * @param headers the headers to set
	 * @param body the text body of the message
	 * @throws SmsException if any header violates requirements
	 */
	public SmsMessage(Map<String,String> headers, String body) {
		this.setHeaders(headers);
		this.setBody(body);
	}
	
	/**
	 * Get the message headers.
	 * @return the message headers as a Map (which you should not modify)
	 */
	public Map<String,String> getHeaders() {
		return headers;
	}

	/**
	 * Set the headers on the message, adding to current headers.
	 * @param headers
	 * @throws SmsException if any header violates requirements
	 */
	public void setHeaders(Map<String,String> headers) {
		for (Entry<String,String> entry : headers.entrySet()) {
			setHeader(entry.getKey(), entry.getValue());
		}
	}

	/** 
	 * Check that a header is present. 
	 * @return true if header is present
	 */
	public boolean hasHeader(String header) {
		return headers.containsKey(header);
	}

	/** 
	 * Get a header value. 
	 * @return the header value or null if it was not present.
	 */
	public String getHeader(String header) {
		return headers.get(header);
	}

	/** 
	 * Get a header value or defaultValue if the header is not set. 
	 */
	public String getHeader(String header, String defaultValue) {
		return headers.getOrDefault(header, defaultValue);
	}

	/** 
	 * Add or set a header to a value.  If value is empty or null, the header will be removed
	 * with a logged warning.
	 * @param header name of the header, may contain any of A-Z, a-z, 0-9, dash, underscore
	 * @param value any content except newline or line break, will be trimmed
	 * @throws SmsException when header has invalid syntax
	 */
	public void setHeader(String header, String value) {

		Matcher matcher = HEADER_NAME_REGEX.matcher(header);
		
		if (!matcher.matches()) {
		    throw new IllegalArgumentException("Invalid header name: " + header);
		}
		else if (value == null || value.trim().isEmpty()) {
			LOG.warn("Header set to empty or null value is removed: " + header);
			removeHeader(header);
		}
		else if (value.contains("\n") || value.contains("\r")) {
			throw new IllegalArgumentException("Invalid header value: must not contain newline or line break characters");
		}
		else {
			headers.put(header, value.trim());
		}
	}
	
	/**
	 * Set header to standard SMS-formatted date
	 * @param header name of the header to set
	 * @param value date object
	 * @throws SmsException when header has no valid syntax
	 */
	public void setTimestampHeader(String header, Date date) {
		setHeader(header, DATE_FORMAT.format(date));
	}
	
	/**
	 * Add the headerLine to the headers, where headerline is parsed into name and value.
	 * @param headerLine a line of the format 'Name: value'
	 */
	public void addHeader(String headerLine) {

		Matcher matcher = HEADER_REGEX.matcher(headerLine);	
		
		if (matcher.matches()) {
			setHeader(matcher.group(1), matcher.group(2));				
		}
		else {
			throw new IllegalArgumentException("Invalid message header: '" + headerLine + "'");
		}
	}

	/**
	 * Remove a header from the message headers.
	 * @param header the header to remove
	 */
	public void removeHeader(String header) {
		headers.remove(header);
	}
	
	/** 
	 * The text body of the message, never null.
	 * @return the message body 
	 */
	public String getBody() {
		return body;
	}

	/** 
	 * Set the text body of the message.
	 * @param body may have any length; if null the body will be empty
	 */
	public void setBody(String body) {
		if (body == null) {
			body = EMPTY_BODY;
		}
		this.body = body;
	}
	
	/**
	 * Return the serialised representation of the message
	 * @return the String representing the message
	 */
	public String asString() {
		StringBuilder builder = new StringBuilder();
		
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}

		builder.append("\n");
		builder.append(body);
		
		return builder.toString();
	}
	
    /**
     * Return the representation of the message as a byte array
     * @return the byte[] representing the message
     */
    public byte[] asBytes() {
        return asString().getBytes(StandardCharsets.UTF_8);
    }
    
	/**
	 * Read the message from the messageString.
	 * Headers will be added to any that may be present.  The message body
	 * if any will be replaced.
	 * @throws IllegalArgumentException when the message does not meet syntax rules
	 */
	public void read(String messageString) {
		read(new StringReader(messageString));
	}
	
    /**
     * Read the message from the array of bytes
     * @param bytes
     */
    public void read(byte[] bytes) {
        read(new String(bytes, StandardCharsets.UTF_8));
    }
    
	/**
	 * Read the message from the serialised representation in Reader.
	 * Headers will be added to any that may be present.  The message body
	 * if any will be replaced.
	 * @throws IllegalArgumentException when the message does not meet syntax rules
	 */
	public void read(Reader reader) {
		read(new BufferedReader(reader).lines().iterator());
	}
	
	/**
	 * Read message from a file on the filesystem.
	 * Headers will be added to any that may be present.  The message body
	 * if any will be replaced.
	 * @param path file name to read from
	 * @throws IOException when underlying infrastructure throws it
	 * @throws IllegalArgumentException when the message does not meet syntax rules
	 */
	public void read(Path path) throws IOException {
		read(Files.newBufferedReader(path));
	}
	
	/**
	 * Read message from a list of lines.
	 * Headers will be added to any that may be present.  The message body
	 * if any will be replaced.
	 * @throws IllegalArgumentException when the message does not meet syntax rules
	 */
	public void read(Iterator<String>  iter) {
		parseHeaders(iter);
		parseBody(iter);
	}
	
	/**
	 * Write the message to file.
	 * @param path file name to write to
	 * @throws IOException when underlying infrastructure throws it
	 * @throws IllegalArgumentException when the message is invalid
	 */
	public void writeFile(Path path) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
		
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			writer.write(entry.getKey());
			writer.write(": ");
			writer.write(entry.getValue());
			writer.write('\n');
		}
		
		writer.write("\n");
		writer.write(body);
		
		writer.close();
	}

	/**
	 * Parse zero or more header lines upto and including the mandatory terminating empty line.
	 * @param iter iterator over a list of lines
	 * @throws IllegalArgumentException at the first invalid header line or when the headers are not
	 * 	terminated by an empty line
	 */
	private void parseHeaders(Iterator<String> iter) {
		
		while (iter.hasNext()) {
			String line = iter.next().trim();
			if (line.length() == 0) {
				return;
			}
			addHeader(line);
		}
		
		throw new IllegalArgumentException("Invalid message: headers not terminated by empty line");
	}

	/**
	 * Parses the possibly multiline body of a message, returning it as a String.
	 * @param iter points to the first line of the list of strings making up the body
	 * @return the concatenated strings making up the body
	 */
	private void parseBody(Iterator<String> iter) {
		
		StringBuilder builder = new StringBuilder();
		if (iter.hasNext()) {
			builder.append(iter.next());
			while (iter.hasNext()) {
				builder.append("\n").append(iter.next());
			}
		}
		
		setBody(builder.toString());
	}

	@Override
	public String toString() {
		String to = "to " + getHeader("To", "(unset)");
		return "SmsMessage " + to + ": " + body.substring(0, 20) + " ...";
	}
}
