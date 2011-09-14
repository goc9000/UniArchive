/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.graphics;

import java.net.URL;

import javax.swing.ImageIcon;

/**
 * A class for storing information regarding a smiley.
 */
public class Smiley
{
	public final URL url;
	public final int width;
	public final int height;
	public final ImageIcon icon;
	
	/**
	 * Constructor.
	 * 
	 * @param url An URL to the smiley.
	 */
	protected Smiley(URL url)
	{
		this.url = url;
		this.icon = new ImageIcon(url);
		this.width = this.icon.getIconWidth();
		this.height = this.icon.getIconHeight();
	}
}
