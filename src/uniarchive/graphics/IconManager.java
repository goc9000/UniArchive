/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.graphics;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;

/**
 * An object for the loading of icons from the graphics
 * folder and for performing basic operations on them
 * (composition, rendering them as disabled, etc.)
 * 
 * This class is modeled as a singleton.
 */
public class IconManager
{
	protected static IconManager _instance = null;
	
	protected Map<String, ImageIcon> _requestCache = new TreeMap<String, ImageIcon>();
	protected List<String> _paths = new ArrayList<String>();
	protected List<String> _extensions = new ArrayList<String>();
	
	/**
	 * Constructor.
	 */
	private IconManager()
	{
		this._paths.add("");
		this._paths.add("actions");
		this._paths.add("items");
		this._paths.add("kibble");
		this._paths.add("services");
		this._extensions.add(".png");
		this._extensions.add(".gif");
	}
	
	/**
	 * Gets the icon specified by a given request string (usually
	 * the icon name, plus any trasformations).
	 * 
	 * The full grammar for an icon request is as follows:
	 * 
	 * request ::= composited
	 * composite ::= transformed ('+' transformed)*
	 * transformed ::= icon (':' transformation)*
	 * icon ::= resource | '{' request '}'
	 * resource ::= regex:[^}|]+ | '"' regex:[^"] '"'
	 * transformation ::= "disabled"
	 * 
	 * @param iconName The request string
	 * @return The corresponding icon, or null if it does
	 *         not exist
	 */
	public ImageIcon getIcon(String iconName)
	{
		if (this._requestCache.containsKey(iconName)) return this._requestCache.get(iconName);

		ImageIcon resolution = null;
		try
		{
			resolution = this._resolveIconRequest(iconName, new ParsePosition(0));
		}
		catch (Exception e)
		{
		}
		this._requestCache.put(iconName, resolution);
		
		return resolution;
	}
	
	/**
	 * Returns the single instance of the icon manager.
	 *  
	 * @return The icon manager object
	 */
	public static IconManager getInstance()
	{
		if (IconManager._instance == null) IconManager._instance = new IconManager();
		
		return IconManager._instance;
	}
	
	/**
	 * Recursively resolves an icon request, executing
	 * any required compositing or transformations
	 * along the way, if required.
	 * 
	 * This handles the REQUEST definition in the BNF.
	 * 
	 * @param iconName The name of the icon to obtain, plus any
	 *                 transformation specifiers.
	 * @param pos The current parsing position in the icon name
	 * @return The transformed icon
	 */
	protected ImageIcon _resolveIconRequest(String iconName, ParsePosition pos)
	{
		return this._resolveIconComposite(iconName, pos);
	}
	
	/**
	 * Recursively resolves an icon request, executing
	 * any required compositing or transformations
	 * along the way, if required.
	 * 
	 * This handles the COMPOSITE definition in the BNF.
	 * 
	 * @param iconName The name of the icon to obtain, plus any
	 *                 transformation specifiers.
	 * @param pos The current parsing position in the icon name
	 * @return The transformed icon
	 */
	protected ImageIcon _resolveIconComposite(String iconName, ParsePosition pos)
	{
		ImageIcon firstIcon = this._resolveIconTransformed(iconName, pos);
		BufferedImage buffer = null;
		Graphics2D canvas = null;
		
		while (true)
		{
			// Check whether another term follows
			if (pos.getIndex() >= iconName.length()) break;
			if (iconName.charAt(pos.getIndex()) != '+') break;
			pos.setIndex(pos.getIndex()+1);
			
			ImageIcon term = this._resolveIconTransformed(iconName, pos);
			
			// Initialize buffer
			if (buffer == null)
			{
				buffer = new BufferedImage(firstIcon.getIconWidth(), firstIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				canvas = buffer.createGraphics();
				canvas.drawImage(firstIcon.getImage(), 0, 0, null);
			}
			
			// Compose icon atop buffer
			canvas.drawImage(term.getImage(), 0, 0, null);
		}
		
		return (buffer == null) ? firstIcon : new ImageIcon(buffer);
	}
	
	/**
	 * Recursively resolves an icon request, executing
	 * any required compositing or transformations
	 * along the way, if required.
	 * 
	 * This handles the TRANSFORMED definition in the BNF.
	 * 
	 * @param iconName The name of the icon to obtain, plus any
	 *                 transformation specifiers.
	 * @param pos The current parsing position in the icon name
	 * @return The transformed icon
	 */
	protected ImageIcon _resolveIconTransformed(String iconName, ParsePosition pos)
	{
		final Pattern PAT_TRANS_NAME = Pattern.compile("^[^:}+]+", Pattern.CASE_INSENSITIVE);
		
		ImageIcon baseIcon = this._resolveIconIcon(iconName, pos);
		BufferedImage buffer = null;
		Graphics2D canvas = null;
		int[] bits = null;
		
		while (true)
		{
			// Check whether another transformation follows
			if (pos.getIndex() >= iconName.length()) break;
			if (iconName.charAt(pos.getIndex()) != ':') break;
			pos.setIndex(pos.getIndex()+1);
			
			// Fetch transformation name
			Matcher match = PAT_TRANS_NAME.matcher(iconName.substring(pos.getIndex()));
			if (!match.find()) throw new RuntimeException("Expecting: transformation");
			String tranName = match.group(0);
			pos.setIndex(pos.getIndex()+tranName.length());
			
			// Initialize buffer
			if (buffer == null)
			{
				buffer = new BufferedImage(baseIcon.getIconWidth(), baseIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				canvas = buffer.createGraphics();
				canvas.drawImage(baseIcon.getImage(), 0, 0, null);
				bits = new int[baseIcon.getIconWidth()*baseIcon.getIconHeight()];
			}
			
			// Execute transformation
			buffer.getRGB(0, 0, buffer.getWidth(), buffer.getHeight(), bits, 0, buffer.getWidth());
			
			if (tranName.equals("disabled"))
			{
				// Render the icon as disabled: it will be desaturated by luminosity
				// and the alpha values will be halved
				for (int i=0; i<bits.length; i++)
				{
					int a = (bits[i] >> 24) & 0xFF;
					int r = (bits[i] >> 16) & 0xFF;
					int g = (bits[i] >> 8) & 0xFF;
					int b = (bits[i] >> 0) & 0xFF;
					
					final double NORM = Math.sqrt(Math.pow(0.5, 2.0)+Math.pow(1.0, 2.0)+Math.pow(0.25, 2.0));
					int lum = (int)(Math.sqrt(Math.pow(r*0.5, 2.0)+Math.pow(g*1.0, 2.0)+Math.pow(b*0.25, 2.0))/NORM);
					
					a /= 2;
					r = lum;
					g = lum;
					b = lum;
					
					bits[i] = (((((a << 8)+r)<<8)+g)<<8)+b;
				}
			}
			else
			{
				throw new RuntimeException("Unsupported transformation: '"+tranName+"'");
			}
			
			buffer.setRGB(0, 0, buffer.getWidth(), buffer.getHeight(), bits, 0, buffer.getWidth());
		}
		
		return (buffer == null) ? baseIcon : new ImageIcon(buffer);
	}
	
	/**
	 * Recursively resolves an icon request, executing
	 * any required compositing or transformations
	 * along the way, if required.
	 * 
	 * This handles the ICON definition in the BNF.
	 * 
	 * @param iconName The name of the icon to obtain, plus any
	 *                 transformation specifiers.
	 * @param pos The current parsing position in the icon name
	 * @return The transformed icon
	 */
	protected ImageIcon _resolveIconIcon(String iconName, ParsePosition pos)
	{
		final Pattern PAT_RESOURCE_NAME = Pattern.compile("^(?:(?:[^\"][^}+:]*)|(?:\"[^\"]*\"))", Pattern.CASE_INSENSITIVE);
		
		// Try the '{' request '}' alterative
		if ((pos.getIndex() < iconName.length()) && iconName.charAt(pos.getIndex()) == '{')
		{
			pos.setIndex(pos.getIndex()+1);
			ImageIcon result = this._resolveIconRequest(iconName, pos);
			if (iconName.charAt(pos.getIndex()) != '}') throw new RuntimeException("Expecting '}'");
			pos.setIndex(pos.getIndex()+1);
			return result;
		}
		
		// Try the resource-name alternative
		Matcher match = PAT_RESOURCE_NAME.matcher(iconName.substring(pos.getIndex()));
		if (!match.find()) throw new RuntimeException("Expecting: resource");
		String resName = match.group(0);
		pos.setIndex(pos.getIndex()+resName.length());
		
		if (resName.startsWith("\"")) resName = resName.substring(1, resName.length()-1);
		
		// Find the resource, using the search paths and possible extensions lists
		URL url = null;
		for (String path : this._paths)
		{
			for (String ext : this._extensions)
			{
				url = this.getClass().getResource(
						path+
						((path != "") ? "/" : "")+
						resName+
						((resName.endsWith(ext)) ? "" : ext));
				if (url != null) break;
			}
			if (url != null) break;
		}
		if (url == null) throw new RuntimeException("Resource '"+resName+"' not found");

		return new ImageIcon(url);
	}
}
