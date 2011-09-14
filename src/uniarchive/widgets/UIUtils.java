/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import uniarchive.graphics.IconManager;

/**
 * Abstract class for helping with common UI creation tasks.
 */
public abstract class UIUtils
{
	/**
	 * Creates a command button with an icon and registers it with
	 * an action listener with a given command string.
	 * 
	 * @param caption The button's caption. If the it is flanked by asterisks, the
	 *                caption will be rendered in bold text.
	 * @param icon The name of the icon that will be used for this button. The Graphics
	 *             Manager will be called to load the normal and disabled icons,
	 *             respectively.
	 * @param listener An ActionListener that will be notified when the button is clicked.
	 * @param command The command string that will be sent to the listener to help
	 *                identify the button.
	 */
	public static JButton makeButton(String caption, String icon, ActionListener listener, String command)
	{
		JButton button = new JButton();
		
		button.addActionListener(listener);
		button.setActionCommand(command);
		
		if (icon != null)
		{
			button.setIcon(IconManager.getInstance().getIcon(icon));
			button.setDisabledIcon(IconManager.getInstance().getIcon("{"+icon+"}:disabled"));
		}
		
		if ((caption != null) && (caption.length()>1) && caption.startsWith("*") && caption.endsWith("*"))
		{
			caption = caption.substring(1, caption.length()-1);
			button.setFont(button.getFont().deriveFont(Font.BOLD));
		}
		
		button.setText(caption);
		if (caption == null) button.setIconTextGap(0);
		
		return button;
	}
	
	/**
	 * Creates a menu item with an icon and registers it with
	 * an action listener with a given command string.
	 * 
	 * @param caption The menu item's text
	 * @param icon The name of the icon that will be used for this button. The Graphics
	 *             Manager will be called to load the normal and disabled icons,
	 *             respectively.
	 * @param listener An ActionListener that will be notified when the item is clicked.
	 * @param command The command string that will be sent to the listener to help
	 *                identify the command.
	 */
	public static JMenuItem makeMenuItem(String caption, String icon, ActionListener listener, String command)
	{
		return UIUtils.makeMenuItem(caption, null, icon, listener, command);	
	}
	
	/**
	 * Creates a menu item with an icon and registers it with
	 * an action listener with a given command string.
	 * 
	 * @param caption The menu item's text. You may precede a character with an underscore
	 *                to make it the mnemonic.
	 * @param shortcut A string specifying a keyboard shortcut (e.g. "Ctrl+F5") for this
	 *                 menu item. Can be null.
	 * @param icon The name of the icon that will be used for this button. The Graphics
	 *             Manager will be called to load the normal and disabled icons,
	 *             respectively.
	 * @param listener An ActionListener that will be notified when the item is clicked.
	 * @param command The command string that will be sent to the listener to help
	 *                identify the command.
	 */
	public static JMenuItem makeMenuItem(String caption, String shortcut, String icon, ActionListener listener, String command)
	{
		JMenuItem item = new JMenuItem();
		
		item.addActionListener(listener);
		item.setActionCommand(command);
		
		if (icon != null)
		{
			item.setIcon(IconManager.getInstance().getIcon(icon));
			item.setDisabledIcon(IconManager.getInstance().getIcon("{"+icon+"}:disabled"));
		}
		
		item.setText(caption);
		if (shortcut != null) item.setAccelerator(UIUtils.decodeShortcut(shortcut));
		
		return item;
	}
	
	/**
	 * Creates a menu.
	 * 
	 * @param caption The menu's caption. You may precede a character with an underscore
	 *                to make it the mnemonic.
	 * @param icon The name of the icon that will be used for this item. The Graphics
	 *             Manager will be called to load the normal and disabled icons,
	 *             respectively.
	 */
	public static JMenu makeMenu(String caption, String icon)
	{
		JMenu menu = new JMenu();
		
		if (icon != null)
		{
			menu.setIcon(IconManager.getInstance().getIcon(icon));
			menu.setDisabledIcon(IconManager.getInstance().getIcon("{"+icon+"}:disabled"));
		}
		
		menu.setText(caption);
		
		return menu;
	}
	
	/**
	 * Makes a button as wide as another given button. This is
	 * useful for making sure buttons arranged in a column have the
	 * same width.
	 * 
	 * @param toButton The button that will take on the width of another
	 * @param fromButton The button whose width will be copied
	 */
	public static void copyButtonWidth(JButton toButton, JButton fromButton)
	{
		toButton.setPreferredSize(fromButton.getPreferredSize());
		toButton.setMaximumSize(fromButton.getMaximumSize());	
	}
	
	/**
	 * Decodes a string identifying a keyboard shortcut and
	 * returns a corresponding KeyStroke object.
	 * 
	 * Note that an exception is thrown if the string is
	 * invalid.
	 * 
	 * @param shortcut A string identifying a shortcut
	 * @return A KeyStroke object
	 */
	public static KeyStroke decodeShortcut(String shortcut)
	{
		String[] tokens = (shortcut.substring(0, shortcut.length()-1).replaceAll("[+]", " ") +
							shortcut.substring(shortcut.length()-1)).split(" ");
		
		// Compute modifiers
		int modifiers = 0;
		for (int i=0; i<tokens.length-1; i++)
		{
			if (tokens[i].equalsIgnoreCase("ctrl")) { modifiers |= InputEvent.CTRL_DOWN_MASK; }
			else if (tokens[i].equalsIgnoreCase("alt")) { modifiers |= InputEvent.ALT_DOWN_MASK; }
			else if (tokens[i].equalsIgnoreCase("shift")) { modifiers |= InputEvent.SHIFT_DOWN_MASK; }
			else if (tokens[i].equalsIgnoreCase("altgr")) { modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK; }
			else if (tokens[i].equalsIgnoreCase("meta")) { modifiers |= InputEvent.META_DOWN_MASK; }
			else throw new RuntimeException("Unknown modifier: "+tokens[i]);
		}
		
		// Create the keystroke
		String keyName = tokens[tokens.length-1];
		
		KeyStroke result = (keyName.length() == 1) ?
				KeyStroke.getKeyStroke(keyName.charAt(0), modifiers) :
				KeyStroke.getKeyStroke(UIUtils.getKeyCodeByName(keyName), modifiers);
				
		if (result == null) throw new RuntimeException("Invalid accelerator: "+shortcut);
		
		return result;
	}
	
	/**
	 * Returns the keycode for any character or string that
	 * resolves to a VK_ constant name (minus the prefix).
	 * 
	 * @param keyName The name of the key
	 * @return The corresponding VK_ keycode
	 */
	public static int getKeyCodeByName(String keyName)
	{
		try
		{
			return KeyEvent.class.getField("VK_"+keyName.toUpperCase()).getInt(null);
		}
		catch (Exception e)
		{
			return 0;
		}
	}
}
