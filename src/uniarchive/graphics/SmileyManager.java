/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.graphics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import uniarchive.models.NameIndex;
import uniarchive.models.archive.IMService;

/**
 * This class provides facilities for detecting smileys
 * in a conversation and getting their URLs given their
 * representation for a given service.
 * 
 * The smiley manager is provided as a singleton object.
 */
public class SmileyManager
{
	protected static final String ASSIGNMENTS_FILE = "smiley_assignments.txt";
	
	protected static SmileyManager _instance = null;
	
	protected NameIndex<Smiley> _cache = new NameIndex<Smiley>();
	protected Map<IMService, Pattern> _patterns = new TreeMap<IMService, Pattern>();
	
	/**
	 * Constructor.
	 */
	private SmileyManager()
	{
		try
		{
			this._buildSmileyCache();
			
			// Compile detection patterns for each service
			for (IMService service : IMService.values())
			{
				this._patterns.put(service, null);
				
				List<String> smileys = this._cache.getAllNames(service);
				if (smileys.isEmpty()) continue;
				
				this._patterns.put(service, this._compileDetectionPattern(smileys));
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Could not initialize smileys:\n"+e.toString());
		}
	}
	
	/**
	 * Returns the single instance of the smiley manager.
	 *  
	 * @return The smiley manager object
	 */
	public static SmileyManager getInstance()
	{
		if (SmileyManager._instance == null) SmileyManager._instance = new SmileyManager();
		
		return SmileyManager._instance;
	}
	
	/**
	 * Fetches the smiley with a given representation for a
	 * specified service.
	 * 
	 * @param service An IM service
	 * @param representation A representation of the smiley
	 * @return The smiley resource, or null if it was not found
	 */
	public Smiley getSmiley(IMService service, String representation)
	{
		return this._cache.getItem(service, representation);
	}
	
	/**
	 * Retrieves a regex pattern for recognizing all smileys
	 * for a given service.
	 * 
	 * @param service An IM service
	 * @return A regex pattern, or null if there are no
	 *         smileys defined for that service
	 */
	public Pattern getDetectionPattern(IMService service)
	{
		return this._patterns.get(service);
	}
	
	/**
	 * Parses the smiley assignments file and builds a cache
	 * of smiley objects for future retrieval.
	 * 
	 * @throws Exception
	 */
	protected void _buildSmileyCache() throws Exception
	{
		// Open the assignments file
		URL assigFileUrl = this.getClass().getResource(ASSIGNMENTS_FILE);
		if (assigFileUrl == null) throw new RuntimeException("Smiley assignments file not found");
		BufferedReader reader = new BufferedReader(new InputStreamReader(assigFileUrl.openStream()));
		String line;
		IMService currentService = null;
		
		while ((line = reader.readLine()) != null)
		{
			line = line.trim();
			
			// Service change
			if (line.startsWith("[") && line.endsWith("]"))
			{
				currentService = IMService.fromShortName(line.substring(1,line.length()-1));
				continue;
			}
			
			// Comment or blank line
			if (line.startsWith("#") || line.isEmpty()) continue;
			
			// Smiley definition
			String[] parts = line.split("[\\s]+");
			URL resource = this.getClass().getResource("smileys/"+parts[0]);
			if (resource == null) continue;
			
			for (int i=1; i<parts.length; i++)
				this._cache.addItem(currentService, parts[i], new Smiley(resource));
		}
		
		reader.close();
	}
	
	/**
	 * Retrieves a regex pattern for recognizing any of
	 * a number of items in a list, such that longer items
	 * take priority over their prefixes.
	 * 
	 * @param items A list of items
	 * @return A regex pattern
	 */
	protected Pattern _compileDetectionPattern(List<String> items)
	{
		// Put the items in a trie
		Trie root = new Trie(false);
		for (String item : items)
		{
			Trie ptr = root;
			for (int i=0; i<item.length(); i++)
			{
				Character c = new Character(item.charAt(i));
				
				if (!ptr.branches.containsKey(c))
					ptr.branches.put(c, new Trie(i == item.length()-1));
				
				ptr = ptr.branches.get(c);
			}
		}

		// Now let the trie do the work
		return Pattern.compile(root.getRegex());
	}	
	
	/**
	 * Internal class for defining an trie.
	 */
	protected static class Trie
	{
		boolean isEndpoint;
		Map<Character,Trie> branches;
		
		/**
		 * Constructor.
		 * 
		 * @param isEndpoint Whether this node is an endpoint
		 */
		public Trie(boolean isEndpoint)
		{
			this.isEndpoint = isEndpoint;
			this.branches = new HashMap<Character,Trie>();
		}
		
		/**
		 * Returns a regular expression for matching
		 * words stored in this trie.
		 */
		public String getRegex()
		{
			StringBuilder sb = new StringBuilder();
			List<String> alternatives = new ArrayList<String>();
			List<Character> charAlternatives = new ArrayList<Character>();
			
			// Compute alternatives with >1 char
			for (Map.Entry<Character,Trie> e : branches.entrySet())
				if (!e.getValue().branches.isEmpty())
				{
					alternatives.add(this._regexChar(e.getKey())+e.getValue().getRegex());
				}
			
			// Compute alternatives with 1 char
			for (Map.Entry<Character,Trie> e : branches.entrySet())
				if (e.getValue().branches.isEmpty())
				{
					charAlternatives.add(e.getKey());
				}
			
			if (!charAlternatives.isEmpty())
			{
				sb.setLength(0);
				if (charAlternatives.size() > 1) sb.append("[");
				for (int i=0; i<charAlternatives.size(); i++)
				{
					sb.append(this._regexChar(charAlternatives.get(i)));
				}
				if (charAlternatives.size() > 1) sb.append("]");
				
				alternatives.add(sb.toString());
			}
			
			// Add null alternative
			if (this.isEndpoint) alternatives.add("");
			
			sb.setLength(0);
			if (alternatives.size() > 1) sb.append("(");
			for (int i=0; i<alternatives.size(); i++)
			{
				if (i>0) sb.append("|");
				sb.append(alternatives.get(i));
			}
			if (alternatives.size() > 1) sb.append(")");
			
			return sb.toString();
		}
		
		/**
		 * Returns an escaped representation of a character
		 * in a regular expression.
		 * 
		 * @param c Any character
		 * @return An escaped representation of the character
		 */
		protected String _regexChar(Character c)
		{
			if (Character.isLetterOrDigit(c)) return ""+c;
			
			return String.format("\\u%04x", (int)c);
		}
	}
}
