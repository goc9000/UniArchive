/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * GaimFileReader implementation for plaintext conversation
 * files. Note that an error-reporting UTF-8 decoder is
 * automatically used.
 */
public class GaimPlaintextReader extends GaimFileReader
{
	protected BufferedReader _buffReader;
	
	/**
	 * Constructor.
	 * 
	 * @param file The conversation file to be read
	 */
	public GaimPlaintextReader(File file) throws Exception
	{
		super(file);
		
		CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPORT);
		decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
		
		BufferedInputStream rawStream = new BufferedInputStream(new FileInputStream(file));
		this._buffReader = new BufferedReader(new InputStreamReader(rawStream, decoder));
	}
	
	@Override
	protected String _reallyReadLine() throws Exception
	{
		return this._buffReader.readLine();
	}
}
