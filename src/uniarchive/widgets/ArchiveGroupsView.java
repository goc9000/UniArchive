/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Component;
import java.awt.Font;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.AbstractButton;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import uniarchive.forms.ArchiveOperationsDialog;
import uniarchive.graphics.IconManager;
import uniarchive.models.SingletonList;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchiveEvent;
import uniarchive.models.archive.IMArchiveListener;
import uniarchive.models.archive.IMService;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for a component that offers a tree-like
 * view of the groups and contacts in the archive,
 * and their associated conversations.
 */
public class ArchiveGroupsView extends JTree
{
	private static final long serialVersionUID = 1L;

	public static enum ItemType { NOTHING, ARCHIVE, GROUP, IDENTITY, CONTACT, ACCOUNT, IDENT_ACCOUNT, UNDEFINED, MIXED };
	
	protected static final String COMMAND_CREATE_GROUP = "createGroup";
	protected static final String COMMAND_CREATE_CONTACT = "createContact";
	protected static final String COMMAND_CREATE_IDENTITY = "createIdentity";
	protected static final String COMMAND_DELETE_ITEM = "deleteItem";
	protected static final String COMMAND_DELETE_SELECTED = "deleteSelected";
	protected static final String COMMAND_MOVE_ITEM = "moveItem";
	protected static final String COMMAND_MOVE_SELECTED = "moveSelected";
	protected static final String COMMAND_MERGE_ITEM = "mergeItem";
	protected static final String COMMAND_MERGE_SELECTED = "mergeSelected";
	protected static final String COMMAND_RENAME_ITEM = "renameItem";
	
	protected final static Object NULL_ARCHIVE = "No archive connected";
	
	protected static XferHandler _commonXferHandler = new XferHandler();
	protected static int _nextControlUID = 1;
	
	public final int controlUID;
	
	protected ArchiveOperationsDialog _manipDialog = null;
	protected JPopupMenu _popupMenu;
	protected JMenuItem _menuItemCreateGroup;
	protected JMenuItem _menuItemCreateContact;
	protected JMenuItem _menuItemCreateIdentity;
	protected JMenuItem _menuItemDeleteItem;
	protected JMenuItem _menuItemRenameItem;
	protected JMenuItem _menuItemMoveItem;
	protected JMenuItem _menuItemMergeItem;
	
	protected Object _contextItem = null;
	protected XferHandler.Operation _pendingDDOperation = null;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	
	/**
	 * Constructor.
	 */
	public ArchiveGroupsView()
	{
		super();
		
		this.controlUID = ArchiveGroupsView._nextControlUID++;
		
		this.setModel(new Model());
		this.setEditable(true);
		
		this.setDropMode(DropMode.ON_OR_INSERT);
		this.setDragEnabled(true);
		this.setTransferHandler(ArchiveGroupsView._commonXferHandler);

		Renderer renderer = new Renderer();
		Editor editor = new Editor(this, renderer);
		this.setCellRenderer(renderer);
		this.setCellEditor(editor);
		
		editor.addCellEditorListener(new CellEditorListener()
		{
			@Override
			public void editingCanceled(ChangeEvent ev)
			{
			}

			@Override
			public void editingStopped(ChangeEvent ev)
			{
				_doFinishRenameItem();
			}			
		});
		
		this._createPopupMenu();
	}
	
	/**
	 * Sets the archive viewed by this control.
	 * 
	 * @param archive The new archive object that is to
	 *                be viewed.
	 */
	public void setArchive(IMArchive archive)
	{
		((Model)this.getModel()).setArchive(archive);
		this.expandGroups();
	}
	
	/**
	 * Gets the archive viewed by this control.
	 * 
	 * @return The currently viewed archive object.
	 */
	public IMArchive getArchive()
	{
		return ((Model)this.getModel()).getArchive();
	}
	
	/**
	 * Makes sure all groups in this tree are
	 * expanded.
	 */
	public void expandGroups()
	{
		IMArchive archive = this.getArchive();
		
		if (archive == null) return;
		
		TreePath root = new TreePath(archive);
		for (Group group : archive.getGroups())
		{
			this.expandPath(root.pathByAddingChild(group));
		}
	}
	
	/**
	 * Gets a list of all selected objects in this archive.
	 * The raw selection is processed so that the selected items
	 * do not nest (i.e. if a node is selected and one of its
	 * ancestors is also selected, only the ancestor will be
	 * considered).
	 *  
	 * @return An list of selected items.
	 */
	public List<Object> getSelection()
	{
		Set<Group> selectedGroups = new TreeSet<Group>();
		Set<Contact> selectedContacts = new TreeSet<Contact>();
		
		List<Object> selection = new ArrayList<Object>();
		TreePath[] paths = this.getSelectionPaths();
		
		if ((this.getArchive() == null) || (paths == null)) return selection;
	
		for (TreePath path : paths)
		{
			Object item = path.getLastPathComponent();
			
			if (item instanceof Group)
			{
				selectedGroups.add((Group)item);
			}
			else if (item instanceof Contact)
			{				
				if (selectedGroups.contains((Group)path.getPathComponent(1))) continue;
				selectedContacts.add((Contact)item);
			}
			else if (item instanceof Account)
			{
				if (selectedGroups.contains((Group)path.getPathComponent(1))) continue;
				if (selectedContacts.contains((Contact)path.getPathComponent(2))) continue;
			}
			
			selection.add(item);
			if (item instanceof IMArchive) break;
		}
	
		return selection;
	}
	
	/**
	 * Quickly checks whether this control has at least one
	 * selected item.
	 * 
	 * @return True if there is at least one selected item,
	 *         false otherwise.
	 */
	public boolean hasSelection()
	{
		if (this.getArchive() == null) return false;
		
		return (this.getSelectionPath() != null);
	}
	
	/**
	 * Checks whether this control has a homogenous
	 * selection.
	 * 
	 * Note that contacts and identity contacts are
	 * considered separate kinds of objects.
	 * 
	 * @return True if there is at least one selected item,
	 *         and all items are of the same type.
	 */
	public boolean hasHomogenousSelection()
	{
		ItemType type = this.getSelectionType();
		
		return ((type != ItemType.NOTHING) && (type != ItemType.MIXED));
	}
	
	/**
	 * Returns the correct number of elements in the current
	 * selection. The standard getSelectionCount() function
	 * is not appropriate, as getSelection() may omit some
	 * elements from the "raw" selection.
	 * 
	 * @return The number of elements returned by getSelection()
	 * @see getSelection()
	 */
	public int getRealSelectionCount()
	{
		return this.getSelection().size();
	}
	
	/**
	 * Checks whether a 'delete selection' command is
	 * applicable, i.e. there is a selection and all
	 * items are deletable.
	 * 
	 * @return True if the command is applicable, false
	 *         otherwise 
	 */
	public boolean canDeleteSelection()
	{
		if (!this.hasSelection()) return false;
		
		for (Object item : this.getSelection()) if (!this._canDeleteItem(item)) return false;
		
		return true;
	}
	
	/**
	 * Checks whether a 'rename selection' command is
	 * applicable, i.e. a single item is selected and it
	 * is renamable.
	 * 
	 * @return True if the command is applicable, false
	 *         otherwise 
	 */
	public boolean canRenameSelection()
	{
		if (this.getRealSelectionCount() != 1) return false;
	
		return this._canRenameItem(this.getSelection().get(0));
	}
	
	/**
	 * Checks whether a 'merge selection' command is
	 * applicable, i.e. the selection is homogenous
	 * and all of the items are mergeable.
	 * 
	 * @return True if the command is applicable, false
	 *         otherwise 
	 */
	public boolean canMergeSelection()
	{
		if (!this.hasHomogenousSelection()) return false;
		
		for (Object item : this.getSelection()) if (!this._canMergeItem(item)) return false;
		
		return true;
	}
	
	/**
	 * Checks whether a 'move selection' command is
	 * applicable, i.e. the selection is homogenous and
	 * all of the items are movable.
	 * 
	 * @return True if the command is applicable, false
	 *         otherwise 
	 */
	public boolean canMoveSelection()
	{
		if (!this.hasHomogenousSelection()) return false;
		
		for (Object item : this.getSelection()) if (!this._canMoveItem(item)) return false;
		
		return true;
	}
	
	/**
	 * Checks whether a given node can be renamed.
	 * 
	 * @param path A TreePath identifying the node
	 * @return True if the node can be edited (renamed),
	 *         false otherwise.
	 */
	@Override
	public boolean isPathEditable(TreePath path)
	{
		if (this.getArchive() == null) return false;
		
		return this._canRenameItem(path.getLastPathComponent());
	}
	
	/**
	 * Returns the type of object contained in the homogenous
	 * selection.
	 * 
	 * If the selection is heterogenous, returns ItemType.MIXED.
	 * If there is no selection, returns ItemType.NOTHING.
	 * 
	 * @return An ItemType constant
	 */
	public ItemType getSelectionType()
	{
		ItemType type = ItemType.NOTHING;
		
		for (Object item : this.getSelection())
		{
			ItemType myType = this.getItemType(item);
			
			if (myType != type)
			{
				if (type != ItemType.NOTHING) return ItemType.MIXED;
				type = myType;
			}
		}
		
		return type;
	}
	
	/**
	 * Describes the current selection in a manner appropriate
	 * for use in a menu command label.
	 * 
	 * @return A string describing the current selection
	 */
	public String getSelectionName()
	{
		List<Object> selection = this.getSelection();
		
		if (selection.size() == 0) return "Selection";
		if (selection.size() > 1)
		{
			String itemName;
			ItemType type = this.getSelectionType();
			
			switch (type)
			{
			case ARCHIVE: itemName = "Archives"; break;
			case GROUP: itemName = "Groups"; break;
			case IDENTITY: itemName = "Identities"; break;
			case CONTACT: itemName = "Contacts"; break;
			case ACCOUNT: case IDENT_ACCOUNT: itemName = "Accounts"; break;
			case MIXED: itemName = "Items"; break;
			default: itemName = "???"; break;
			}
			
			return ""+selection.size()+" Selected "+itemName;
		}
		
		return "Selected "+this.getItemName(selection.get(0));
	}
	
	/**
	 * Names an item (group, contact, account, etc.) in this
	 * control for use in a menu command label.
	 * 
	 * @param item The item to be named
	 * @return A string identifying the type and name of the item
	 */
	public String getItemName(Object item)
	{
		if (item == null) return "Nothing";
		if (item instanceof IMArchive) return "Archive";
		if (item instanceof Group) return "Group '"+(((Group)item).name)+"'";
		if (item instanceof Contact)
			return (((Contact)item).isIdentity() ? "Identity" : "Contact")+
					" '"+(((Contact)item).name)+"'";
		if (item instanceof Account) return "Account '"+(((Account)item).name)+"'";
		
		return "Item";
	}
	
	/**
	 * Gets the type of an item as an ItemType constant.
	 * Types correspond closely to classes, but contacts and
	 * identity contacts are viewed as different types.
	 * 
	 * @param item The item whose type is to be determined
	 * @return A corresponding ItemType constant 
	 */
	public ItemType getItemType(Object item)
	{
		if (item == null) return ItemType.NOTHING;
		if (item instanceof IMArchive) return ItemType.ARCHIVE;
		if (item instanceof Group) return ItemType.GROUP;
		if (item instanceof Contact) return ((Contact)item).isIdentity() ? ItemType.IDENTITY : ItemType.CONTACT;
		if (item instanceof Account) return ((Account)item).isIdentityAccount() ? ItemType.IDENT_ACCOUNT : ItemType.ACCOUNT;
		
		return ItemType.UNDEFINED;
	}
	
	/**
	 * Gets the path to a given item in the tree.
	 * 
	 * @param item An item in the tree
	 * @return A TreePath object representing a path to
	 *         the item, or null if the item is not present
	 *         in the tree.
	 */
	public TreePath getItemPath(Object item)
	{
		Model model = (Model)this.getModel();
		Object[] path = model.getPath(item);
		
		if (path == null) return null;
		return new TreePath(path);
	}
	
	/**
	 * Focus an item in the tree by selecting it and
	 * scrolling to its position.
	 * 
	 * @param item The item to focus
	 */
	public void focusItem(Object item)
	{
		TreePath path = this.getItemPath(item);
		
		this.setSelectionPath(path);
		this.scrollPathToVisible(path);
	}
	
	@Override
	public String convertValueToText(Object value, boolean selected,
			boolean expanded, boolean leaf, int row, boolean hasFocus)
	{
		if (value instanceof Group) return ((Group)value).name;
		if (value instanceof Contact) return ((Contact)value).name;
		if (value instanceof Account) return ((Account)value).name;
		
		return "";
	}

	/**
	 * Prompts the user for a group name and adds the
	 * group to the archive.
	 */
	public void doAddGroup()
	{
		IMArchive archive = this.getArchive();
		if (archive == null) return;
		
		this._initManipDialog();
		if (!this._manipDialog.showAddGroup()) return;
		
		String name = this._manipDialog.resultItemName;
		
		try
		{
			if (archive.getGroupByName(name) != null) throw new RuntimeException("A group with that name already exists.");
			this.focusItem(archive.createGroup(name));
		}
		catch (Exception e)
		{
			this._showErrorMessage(e.getMessage());
		}
	}
	
	/**
	 * Prompts the user for a contact name and adds the
	 * contact to the archive. Note that only regular
	 * contacts may be created using this dialog.
	 * 
	 * @param group The suggested group in which to
	 *              place the contact
	 */
	public void doAddContact(Group group)
	{
		IMArchive archive = this.getArchive();
		if (archive == null) return;
		
		this._initManipDialog();
		if (!this._manipDialog.showAddContact(archive.getRegularGroups(), group)) return;
		
		String name = this._manipDialog.resultItemName;
		group = (Group)(this._manipDialog.resultContainer);
		
		try
		{
			if (archive.getContactByName(name) != null) throw new RuntimeException("A contact with that name already exists.");
			this.focusItem(group.createContact(name));
		}
		catch (Exception e)
		{
			this._showErrorMessage(e.getMessage());
		}
	}
	
	/**
	 * Prompts the user for an identity name and adds the
	 * identity contact to the archive.
	 */
	public void doAddIdentity()
	{
		IMArchive archive = this.getArchive();
		if (archive == null) return;
		
		this._initManipDialog();
		if (!this._manipDialog.showAddIdentity()) return;
		
		String name = this._manipDialog.resultItemName;
		
		try
		{
			if (archive.getContactByName(name) != null) throw new RuntimeException("A contact with that name already exists.");
			this.focusItem(archive.getIdentitiesGroup().createContact(name));
		}
		catch (Exception e)
		{
			this._showErrorMessage(e.getMessage());
		}
	}
	
	/**
	 * Deletes all selected items.
	 * 
	 * Note: a confirmation dialog will be shown.
	 */
	public void doDeleteSelected()
	{
		if (this.canDeleteSelection()) this._doDeleteItems(this.getSelection());
	}
	
	/**
	 * Renames the selected item.
	 */
	public void doRenameSelected()
	{
		if (this.canRenameSelection()) this.startEditingAtPath(this.getItemPath(this.getSelection().get(0)));
	}
	
	/**
	 * Starts a merge operation for all selected items.
	 */
	public void doMergeSelected()
	{
		if (this.canMergeSelection()) this.doMergeItems(this.getSelection(), null);
	}
	
	/**
	 * Merges a number of groups or contacts into another.
	 * The user will be prompted to select a destination
	 * (if one is not suggested) and confirm the operation.
	 * 
	 * @param items A list of items to merge into another
	 * @param into The suggested item into which the others
	 *             will be merged. Can be null.
	 */
	public void doMergeItems(List<Object> items, Object into)
	{
		IMArchive archive = this.getArchive();

		items.remove(into); // Don't merge item with self
		if (items.isEmpty()) return; 
		
		List<Object> candidates = null;
		if (items.get(0) instanceof Group) { candidates = new ArrayList<Object>(archive.getRegularGroups()); }
		else if (items.get(0) instanceof Contact)
		{
			candidates = new ArrayList<Object>(
					((Contact)items.get(0)).isIdentity() ?
							archive.getIdentitiesGroup().getContacts() :
							archive.getRegularContacts()
							);
		}
		else return;
		
		this._initManipDialog();
		if (!this._manipDialog.showMergeItems(items, candidates, into)) return;
	
		// Execute operation
		try
		{
			if (items.get(0) instanceof Group)
			{
				for (Object item : items) ((Group)this._manipDialog.resultContainer).merge((Group)item);
			}
			else if (items.get(0) instanceof Contact)
			{
				for (Object item : items) ((Contact)this._manipDialog.resultContainer).merge((Contact)item);
			}
			
			this.focusItem(this._manipDialog.resultContainer);
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), e.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
		}
	}
	
	/**
	 * Starts a move operation for all selected items.
	 */
	public void doMoveSelected()
	{
		if (this.canMoveSelection()) this.doMoveItems(this.getSelection(), null);
	}

	/**
	 * Moves a number of groups, contacts or accounts to
	 * another location. The user will be prompted to select
	 * a destination ONLY if one is not suggested, otherwise
	 * the operation is executed immediately.
	 * 
	 * @param items A list of items to move
	 * @param location The suggested container into which the items
	 *                 will be moved. For groups, this can be either
	 *                 another group (meaning it should be moved before
	 *                 it), or the archive (meaning it should be moved at
	 *                 the end of the list). Can be null.
	 */
	public void doMoveItems(List<Object> items, Object location)
	{
		IMArchive archive = this.getArchive();
		DummyItem dummyGroup = new DummyItem("(at the end)", IconManager.getInstance().getIcon("group"));

		// Remove the items that are already at the specified location
		List<Object> newItems = new ArrayList<Object>();
		for (Object item : items)
		{
			if ((item instanceof Contact) && ((Contact)item).getGroup().equals(location)) continue;
			if ((item instanceof Account) && ((Account)item).getContact().equals(location)) continue;
			
			newItems.add(item);
		}
		items = newItems;
		if (items.isEmpty()) return;
		
		// Assemble a list of locations
		List<Object> locations = null;
		if (items.get(0) instanceof Group)
		{
			locations = new ArrayList<Object>(archive.getRegularGroups());
			locations.add(dummyGroup);
			if (location == archive) location = dummyGroup;
		}
		else if (items.get(0) instanceof Contact) { locations = new ArrayList<Object>(archive.getRegularGroups()); }
		else if (items.get(0) instanceof Account)
		{
			locations = new ArrayList<Object>(
					((Account)items.get(0)).isIdentityAccount() ?
							archive.getIdentitiesGroup().getContacts() :
							archive.getRegularContacts());
		}
		else return;
		
		// Show move dialog if the destination is not already specified
		if ((location == null) || (items.get(0) instanceof Account))
		{
			this._initManipDialog();
			if (!this._manipDialog.showMoveItems(items, locations, location)) return;
			location = this._manipDialog.resultContainer;
		}
		
		// Execute command
		try
		{
			if (items.get(0) instanceof Group)
			{
				if (location == dummyGroup) location = null;
				for (Object item : items) ((Group)item).move((Group)location);
			}
			else if (items.get(0) instanceof Contact)
			{
				for (Object item : items) ((Contact)item).move((Group)location);
			}
			else if (items.get(0) instanceof Account)
			{
				for (Object item : items) ((Account)item).move((Contact)location);
			}
			
			this.focusItem(items.get(0));
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), e.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
		}
	}
	
	/**
	 * Checks whether a given item is deletable.
	 * 
	 * @param item An item in the control 
	 * @return True if the item can be deleted,
	 *         false otherwise.
	 */
	protected boolean _canDeleteItem(Object item)
	{
		switch (this.getItemType(item))
		{
		case ARCHIVE:
		case CONTACT:
		case IDENTITY:
		case ACCOUNT:
		case IDENT_ACCOUNT:
			return true;
		case GROUP: return !((Group)item).isIdentitiesGroup();
		default: return false;
		}
	}
	
	/**
	 * Checks whether a given item is renameable.
	 * 
	 * @param item An item in the control 
	 * @return True if the item can be renamed,
	 *         false otherwise.
	 */
	protected boolean _canRenameItem(Object item)
	{
		switch (this.getItemType(item))
		{
		case CONTACT:
		case IDENTITY:
		case ACCOUNT:
		case IDENT_ACCOUNT:
			return true;
		case GROUP: return !((Group)item).isIdentitiesGroup();
		default: return false;
		}
	}
	
	/**
	 * Checks whether a given item is movable.
	 * 
	 * @param item An item in the control 
	 * @return True if the item can be moved,
	 *         false otherwise.
	 */
	protected boolean _canMoveItem(Object item)
	{
		// Note: indentities may not be moved, as they can only reside
		// in the Identities group
		switch (this.getItemType(item))
		{
		case CONTACT:
		case ACCOUNT:
		case IDENT_ACCOUNT:
			return true;
		case GROUP: return !((Group)item).isIdentitiesGroup();
		default: return false;
		}
	}
	
	/**
	 * Checks whether a given item is mergeable.
	 * 
	 * @param item An item in the control 
	 * @return True if the item can be merged,
	 *         false otherwise.
	 */
	protected boolean _canMergeItem(Object item)
	{
		switch (this.getItemType(item))
		{
		case CONTACT:
		case IDENTITY:
			return true;
		case GROUP: return !((Group)item).isIdentitiesGroup();
		default: return false;
		}
	}
	
	/**
	 * Executes the "create contact" command as called
	 * from the popup menu.
	 */
	protected void _doCreateContactInContext()
	{
		TreePath path = this.getItemPath(this._contextItem);
		Group contextGroup = (path.getPathCount() > 1) ? (Group)path.getPathComponent(1) : null;
		
		if (contextGroup != null) this.doAddContact(contextGroup);
	}
	
	/**
	 * Executes the "rename item" command as called
	 * from the popup menu.
	 */
	protected void _doRenameItemInContext()
	{
		this.startEditingAtPath(this.getItemPath(this._contextItem));
	}
	
	/**
	 * Finishes the "rename item" command as called from
	 * the popup menu (this is called after the editor is
	 * closed).
	 */
	protected void _doFinishRenameItem()
	{
		Object item = ((Editor)this.getCellEditor()).getItemBeingRenamed();
		String newName = (String)(this.getCellEditor().getCellEditorValue());
		
		this.setSelectionPath(null);
		
		try
		{
			TreePath itemPath = this.getItemPath(item);
			boolean wasExpanded = this.isExpanded(itemPath);
			Object newItem = null;
			
			if (item instanceof Account) { newItem = ((Account)item).rename(newName); }
			else if (item instanceof Contact) { newItem = ((Contact)item).rename(newName); }
			else if (item instanceof Group) { newItem = ((Group)item).rename(newName); }
			else return;
			
			if (wasExpanded) this.expandPath(this.getItemPath(newItem));
			this.focusItem(newItem);
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), e.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
		}
	}
	
	/**
	 * Executes the "delete item" command as called
	 * from the popup menu.
	 */
	protected void _doDeleteItemInContext()
	{
		this._doDeleteItems(new SingletonList<Object>(this._contextItem));
	}
	
	/**
	 * Executes the "move item" command as called
	 * from the popup menu.
	 */
	protected void _doMoveItemInContext()
	{
		this.doMoveItems(new SingletonList<Object>(this._contextItem),null);
	}
	
	/**
	 * Executes the "merge item" command as called
	 * from the popup menu.
	 */
	protected void _doMergeItemInContext()
	{
		this.doMergeItems(new SingletonList<Object>(this._contextItem),null);
	}
	
	/**
	 * Deletes a number of items from the archive.
	 * A dialog will be shown, showing the potential
	 * effects of the deletion, and prompting the user
	 * for confirmation.
	 * 
	 * @param items A list of items to delete
	 */
	protected void _doDeleteItems(List<Object> items)
	{
		IMArchive archive = this.getArchive();
		
		// Deleting the archive itself is an entirely
		// different operation, so handle this case first
		if (!items.isEmpty() && (items.get(0) instanceof IMArchive))
		{
			if (JOptionPane.showConfirmDialog(
					this.getTopLevelAncestor(),
					"WARNING: this will irreversibly delete archive \""+archive.getName()+"\".\n"+
					"Do you wish to proceed?",
					"Confirm Archive Deletion",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE
					) != JOptionPane.YES_OPTION) return;
			
			try { archive.delete(); }
			catch (Exception e)
			{ JOptionPane.showMessageDialog(this.getTopLevelAncestor(), e.getMessage(), "Error", JOptionPane.WARNING_MESSAGE); }			
			return;
		}
		
		// Determine the total number of groups and contacts
		// that are about to be deleted, as well as the
		// exact accounts.
		int nGroups = 0;
		int nContacts = 0;
		int nIdentities = 0;
		List<Account> deletedAccounts = new ArrayList<Account>();
		
		for (Object item : items)
		{
			if (item instanceof Account)
			{
				deletedAccounts.add((Account)item);
			}
			else if (item instanceof Contact)
			{
				if (((Contact)item).isIdentity()) nIdentities++; else nContacts++;
				deletedAccounts.addAll(((Contact)item).getAccounts());
			}
			else if (item instanceof Group)
			{
				nGroups++;
				for (Contact contact : ((Group)item).getContacts())
				{
					nContacts++;
					deletedAccounts.addAll(contact.getAccounts());
				}
			}
		}
		int nAccounts = deletedAccounts.size();
		
		// Determine the number of conversations affected
		int nConversations = -1;
		try { nConversations = archive.countDependentConversations(deletedAccounts); } catch (Exception e) {}
		
		// Format the message
		StringBuilder msg = new StringBuilder();
		msg.append("This will cause the deletion of:\n\n");
		if (nGroups > 0) msg.append("- "+nGroups+" group"+((nGroups>1) ? "s" : "")+"\n");
		if (nIdentities > 0) msg.append("- "+nIdentities+" identit"+((nIdentities>1) ? "ies" : "y")+"\n");
		if (nContacts > 0) msg.append("- "+nContacts+" contact"+((nContacts>1) ? "s" : "")+"\n");
		if (nAccounts > 0) msg.append("- "+nAccounts+" account"+((nAccounts>1) ? "s" : "")+"\n");
		if (nConversations > 0) msg.append("- "+nConversations+" conversation"+((nConversations>1) ? "s" : "")+"\n");
		msg.append("\nProceed?");
		
		if (JOptionPane.showConfirmDialog(this.getTopLevelAncestor(), msg.toString(),
				"Confirm", JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
		
		// Perform the deletions
		try
		{
			for (Object item : items)
			{
				if (item instanceof Group) { ((Group)item).delete(); }
				else if (item instanceof Contact) { ((Contact)item).delete(); }
				else if (item instanceof Account) {((Account)item).delete(); }
			}
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), e.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
		}
	}
	
	/**
	 * Shows the popup menu on this control.
	 * 
	 * @param menuX The menu's X coordinate
	 * @param menuY The menu's Y coordinate
	 */
	protected void _showPopupMenu(int menuX, int menuY)
	{
		if (this.getArchive() == null) return;
		
		this._contextItem = this.getClosestPathForLocation(menuX, menuY).getLastPathComponent();
		this._updatePopupMenu();
		this._popupMenu.show(this, menuX, menuY);
	}
	
	/**
	 * Initializes the Archive Manipulation dialog if it has not
	 * been initialized yet. It is necessary for the initialization
	 * to be performed lazily because the owner of the JTree is
	 * not available within the constructor.
	 */
	protected void _initManipDialog()
	{
		if (this._manipDialog != null) return;
		
		this._manipDialog = new ArchiveOperationsDialog((Window)this.getTopLevelAncestor());
	}
	
	/**
	 * Creates the control's popup menu.
	 */
	protected void _createPopupMenu()
	{
		this._menuItemCreateGroup = UIUtils.makeMenuItem("Create Group...", "group+ovl_add", this._cmdButtonListener, COMMAND_CREATE_GROUP);
		this._menuItemCreateContact = UIUtils.makeMenuItem("Create Contact...", "contact+ovl_add", this._cmdButtonListener, COMMAND_CREATE_CONTACT);
		this._menuItemCreateIdentity = UIUtils.makeMenuItem("Create Identity...", "identity+ovl_add", this._cmdButtonListener, COMMAND_CREATE_IDENTITY);
		this._menuItemDeleteItem = UIUtils.makeMenuItem("Delete Item", "delete", this._cmdButtonListener, COMMAND_DELETE_ITEM);
		this._menuItemRenameItem = UIUtils.makeMenuItem("Rename Item", "rename", this._cmdButtonListener, COMMAND_RENAME_ITEM);
		this._menuItemMergeItem = UIUtils.makeMenuItem("Merge Item", "merge", this._cmdButtonListener, COMMAND_MERGE_ITEM);
		this._menuItemMoveItem = UIUtils.makeMenuItem("Move Item", "move", this._cmdButtonListener, COMMAND_MOVE_ITEM);
		
		this._popupMenu = new JPopupMenu();
		this._popupMenu.add(this._menuItemRenameItem);
		this._popupMenu.add(this._menuItemDeleteItem);
		this._popupMenu.add(this._menuItemMergeItem);
		this._popupMenu.add(this._menuItemMoveItem);
		this._popupMenu.add(new JSeparator());
		this._popupMenu.add(this._menuItemCreateGroup);
		this._popupMenu.add(this._menuItemCreateContact);
		this._popupMenu.add(this._menuItemCreateIdentity);
				
		this.addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) _showPopupMenu(e.getX(), e.getY());
		    }
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) _showPopupMenu(e.getX(), e.getY());
		    }
		});
	}
	
	/**
	 * Updates the popup menu according to the current
	 * context item (i.e. the item over which the popup menu
	 * was invoked).
	 */
	protected void _updatePopupMenu()
	{
		Model model = (Model)this.getModel();
		Object[] path = model.getPath(this._contextItem);
		
		Group contextGroup = (path.length > 1) ? (Group)path[1] : null;
		
		this._menuItemRenameItem.setText("Rename "+this.getItemName(this._contextItem));
		this._menuItemRenameItem.setEnabled(this._canRenameItem(this._contextItem));
		
		if (contextGroup == null)
		{
			this._menuItemCreateContact.setText("Create Contact...");
			this._menuItemCreateContact.setEnabled(false);
			this._menuItemCreateContact.setVisible(true);
			this._menuItemCreateIdentity.setVisible(false);
		}
		else if (contextGroup.isIdentitiesGroup())
		{
			this._menuItemCreateContact.setVisible(false);
			this._menuItemCreateIdentity.setVisible(true);
		}
		else
		{
			this._menuItemCreateContact.setText("Create Contact Under '"+contextGroup.name+"'...");
			this._menuItemCreateContact.setEnabled(true);
			this._menuItemCreateContact.setVisible(true);
			this._menuItemCreateIdentity.setVisible(false);
		}
		
		int selectionCount = this.getSelection().size();
		if (selectionCount<2)
		{
			String itemName = this.getItemName(this._contextItem);
			this._menuItemDeleteItem.setText("Delete "+itemName);
			this._menuItemDeleteItem.setEnabled(this._canDeleteItem(this._contextItem));
			this._menuItemDeleteItem.setActionCommand(COMMAND_DELETE_ITEM);
			
			this._menuItemMoveItem.setText("Move "+itemName+"...");
			this._menuItemMoveItem.setEnabled(this._canMoveItem(this._contextItem));
			this._menuItemMoveItem.setActionCommand(COMMAND_MOVE_ITEM);
			
			this._menuItemMergeItem.setText("Merge "+itemName+"...");
			this._menuItemMergeItem.setEnabled(this._canMergeItem(this._contextItem));
			this._menuItemMergeItem.setActionCommand(COMMAND_MERGE_ITEM);
		}
		else
		{
			String selName = this.getSelectionName();
			this._menuItemDeleteItem.setText("Delete "+selName);
			this._menuItemDeleteItem.setEnabled(this.canDeleteSelection());
			this._menuItemDeleteItem.setActionCommand(COMMAND_DELETE_SELECTED);
		
			this._menuItemMoveItem.setText("Move "+selName+"...");
			this._menuItemMoveItem.setEnabled(this.canMoveSelection());
			this._menuItemMoveItem.setActionCommand(COMMAND_MOVE_SELECTED);
			
			this._menuItemMergeItem.setText("Merge "+selName+"...");
			this._menuItemMergeItem.setEnabled(this.canMergeSelection());
			this._menuItemMergeItem.setActionCommand(COMMAND_MERGE_SELECTED);
		}
	}
	
	/**
	 * Shows an error message following an operation initiated
	 * in this control.
	 * 
	 * @param message The message to be shown
	 */
	protected void _showErrorMessage(String message)
	{
		JOptionPane.showMessageDialog(this.getTopLevelAncestor(), message, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	/**
	 * Internal class that listens to click events on command
	 * buttons contained in this form and starts the corresponding
	 * actions.
	 */
	protected class CommandButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			if (evt.getSource() instanceof AbstractButton)
			{
				String commandId = evt.getActionCommand();
				
				if (commandId.equals(ArchiveGroupsView.COMMAND_CREATE_GROUP)) { doAddGroup(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_CREATE_CONTACT)) { _doCreateContactInContext(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_CREATE_IDENTITY)) { doAddIdentity(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_RENAME_ITEM)) { _doRenameItemInContext(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_DELETE_ITEM)) { _doDeleteItemInContext(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_MERGE_ITEM)) { _doMergeItemInContext(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_MOVE_ITEM)) { _doMoveItemInContext(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_DELETE_SELECTED)) { doDeleteSelected(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_MERGE_SELECTED)) { doMergeSelected(); }
				else if (commandId.equals(ArchiveGroupsView.COMMAND_MOVE_SELECTED)) { doMoveSelected(); }
			}
		}
	}
	
	/**
	 * Internal class for rendering cells in this tree
	 * widget.
	 */
	protected static class Renderer extends DefaultTreeCellRenderer
	{
		private static final long serialVersionUID = 1L;

		protected ImageIcon _noArchiveIcon = IconManager.getInstance().getIcon("disconnect");
		protected ImageIcon _archiveIcon = IconManager.getInstance().getIcon("archive");
		protected ImageIcon _groupIcon = IconManager.getInstance().getIcon("group");
		protected ImageIcon _identitiesIcon = IconManager.getInstance().getIcon("identities");
		protected ImageIcon _identityIcon = IconManager.getInstance().getIcon("identity");
		protected ImageIcon _contactIcon = IconManager.getInstance().getIcon("contact");
		
		protected Font _normalFont = null;
		protected Font _boldFont = null;
		
		protected Object _lastItem = null;
		
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value,
				boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			this._lastItem = value;
			
			JLabel comp = (JLabel)super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (this._normalFont == null)
			{
				this._normalFont = comp.getFont();
				this._boldFont = this._normalFont.deriveFont(Font.BOLD);
			}
			
			String text = null;
			if (value instanceof IMArchive) { text = "Archive"; }
			else if (value instanceof Group) { text = ((Group)value).name+" ("+((Group)value).getContacts().size()+")"; }
			else if (value instanceof Contact) { text = ((Contact)value).name; }
			else if (value instanceof Account) { text = ((Account)value).name; }
			else text = value.toString();
			
			comp.setText(text);
			comp.setIconTextGap(6);
			comp.setIcon(this.getItemIcon(value));
			comp.setFont((value instanceof Group) ? this._boldFont : this._normalFont);
			
			return comp;
		}
		
		/**
		 * Gets the icon corresponding to a given item.
		 * 
		 * @param item An item (a group, account, etc.)
		 * @return An icon
		 */
		protected Icon getItemIcon(Object item)
		{
			if (item == NULL_ARCHIVE) return this._noArchiveIcon;
			if (item instanceof IMArchive) return this._archiveIcon;
			if (item instanceof Group) return ((Group)item).isIdentitiesGroup() ? this._identitiesIcon : this._groupIcon;
			if (item instanceof Contact) return ((Contact)item).isIdentity() ? this._identityIcon : this._contactIcon;
			if (item instanceof Account) return ((Account)item).service.icon;
			
			return null;
		}
	}

	/**
	 * Internal class for editing cells in this tree widget.
	 */
	protected static class Editor extends DefaultTreeCellEditor
	{
		protected Object _itemBeingRenamed = null;

		/**
		 * Constructor.
		 * 
		 * @param tree The tree control for which the editor will
		 *             be used
		 * @param renderer The normal cell renderer
		 */
		public Editor(JTree tree, Renderer renderer)
		{
			super(tree, renderer);
		}
		
		/**
		 * Returns the item currently being renamed in the editor.
		 * 
		 * @return An item (account, contact, etc.)
		 */
		public Object getItemBeingRenamed()
		{
			return this._itemBeingRenamed;
		}

		@Override
		protected void determineOffset(JTree tree, Object value, boolean isSelected,
				boolean expanded, boolean leaf, int row)
		{
			super.determineOffset(tree, value, isSelected, expanded, leaf, row);
			
			this._itemBeingRenamed = value;
			this.editingIcon = ((Renderer)this.renderer).getItemIcon(value);
		}	
	}
	
	/**
	 * Internal class for defining the model used by the
	 * above control for interfacing with the IMArchive object.
	 */
	protected class Model implements TreeModel, IMArchiveListener
	{
		protected IMArchive _archive = null;
		protected List<TreeModelListener> _listeners;
		
		/**
		 * Constructor.
		 */
		public Model()
		{
			this._listeners = new ArrayList<TreeModelListener>();
		}
		
		/**
		 * Sets the archive interfaced by this model.
		 * 
		 * @param archive The new archive object that is to
		 *                be viewed.
		 */
		public void setArchive(IMArchive archive)
		{
			if (this._archive != null) this._archive.removeListener(this);
			this._archive = archive;
			if (this._archive != null) this._archive.addListener(this);
			this._fireCompleteTreeChange(this.getRoot());
		}
		
		/**
		 * Gets the archive interfaced by this control.
		 * 
		 * @return The currently viewed archive object.
		 */
		public IMArchive getArchive()
		{
			return this._archive;
		}
		
		@Override
		public Object getRoot()
		{
			return (this._archive != null) ? this._archive : NULL_ARCHIVE;
		}
		
		@Override
		public boolean isLeaf(Object node)
		{
			return (node instanceof Account);
		}
		
		@Override
		public Object getChild(Object node, int index)
		{
			return this.getChildren(node).get(index);
		}
		
		@Override
		public int getChildCount(Object node)
		{
			return this.getChildren(node).size();
		}

		@Override
		public int getIndexOfChild(Object parent, Object node)
		{
			return this.getChildren(parent).indexOf(node);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object node)
		{
		}
		
		@Override
		public void addTreeModelListener(TreeModelListener listener)
		{
			this._listeners.add(listener);
		}
		
		@Override
		public void removeTreeModelListener(TreeModelListener listener)
		{
			this._listeners.remove(listener);	
		}
			
		/**
		 * Obtains the parent of a given node in this model.
		 * 
		 * @param node A node in this model
		 * @return An object representing the parent, or
		 *         null if the node is the root.
		 */
		public Object getParent(Object node)
		{
			if (node instanceof Group) return this._archive;
			if (node instanceof Contact) return ((Contact)node).getGroup();
			if (node instanceof Account) return ((Account)node).getContact();
			
			return null;
		}
		
		/**
		 * Obtains the children of a given node in this model.
		 * 
		 * @param node A node in this model
		 * @return A list of the node's children
		 */
		public List<?> getChildren(Object node)
		{
			if (node instanceof IMArchive) return this._archive.getGroups();
			if (node instanceof Group) return ((Group)node).getContacts();
			if (node instanceof Contact) return ((Contact)node).getAccounts();
			
			return Collections.EMPTY_LIST;
		}
		
		/**
		 * Gets the path of a given node.
		 * 
		 * @param node A node in this model
		 * @return An array of nodes starting with the
		 *         root and ending in this node
		 */
		public Object[] getPath(Object node)
		{
			// Determine path length
			int pathLength = 0;
			Object ptr = node;
			while (ptr != null)
			{
				pathLength++;
				ptr = this.getParent(ptr);
			}
			
			// Fill in path
			Object[] path = new Object[pathLength];
			ptr = node;
			for (int i=pathLength-1; i>=0; i--)
			{
				path[i] = ptr;
				ptr = this.getParent(ptr);
			}
			
			return path;
		}
		
		/**
		 * Reacts to an event in the archive queried by this
		 * control.
		 * 
		 * @param event An event describing the change that
		 *              has occured
		 */
		public void archiveChanged(IMArchiveEvent event)
		{
			// Events that affect no items, or pertain to conversations, are ignored.
			if (event.items.isEmpty()) return;
			if (event.items.get(0) instanceof Conversation) return;
			
			// Special processing for whole archive deletion messages
			if (!event.isEmpty() && (event.items.get(0) instanceof IMArchive))
			{
				if (event.type == IMArchiveEvent.Type.DELETING_ITEMS) return;
				if (event.type == IMArchiveEvent.Type.DELETED_ITEMS)
				{
					this.setArchive(null);
					return;
				}
			}
			
			switch (event.type)
			{
			case MAJOR_CHANGE:
				this._fireTreeStructureChanged(event.items.get(0));
				expandGroups();
				break;
			case ADDED_ITEMS:
			case MOVING_ITEMS:
			case MOVED_ITEMS:
			case UPDATING_ITEMS:
			case DELETING_ITEMS:
			case UPDATED_ITEMS:
				// Compute parent, and items and indexes vectors
				Object parent = this.getParent(event.items.get(0));
				
				Set<Object> itemSet = new TreeSet<Object>(event.items);
				int[] indexes = new int[event.items.size()];
				Object[] items = new Object[event.items.size()];
				int inItems = 0;
				List<? extends Object> children = this.getChildren(parent);
				for (int i=0; i<children.size(); i++)
					if (itemSet.contains(children.get(i)))
					{
						indexes[inItems] = i;
						items[inItems++] = children.get(i);
					}
				
				switch(event.type)
				{
				case ADDED_ITEMS:
				case MOVED_ITEMS:
				case UPDATED_ITEMS:
					this._fireTreeNodesInserted(parent, indexes, items);
					break;
				case DELETING_ITEMS:
				case MOVING_ITEMS:
				case UPDATING_ITEMS:
					this._fireTreeNodesRemoved(parent, indexes, items);
					break;
				}
				break;
			}
		}
		
		/**
		 * Fires a 'tree nodes inserted' event for any
		 * interested listeners.
		 * 
		 * @param parent The parent of the inserted nodes
		 * @param itemIndexes An array of indices indicating
		 *                    where the items were inserted
		 * @param items An array of inserted items
		 */
		protected void _fireTreeNodesInserted(Object parent, int[] itemIndexes, Object[] items)
		{
			Object[] path = this.getPath(parent);
			TreeModelEvent event = new TreeModelEvent(this, path, itemIndexes, items);
			
			for (TreeModelListener listener : this._listeners)
				listener.treeNodesInserted(event);
		}
		
		/**
		 * Fires a 'tree nodes removed' event for any
		 * interested listeners.
		 * 
		 * @param parent The parent of the inserted nodes
		 * @param itemIndexes An array of indices indicating
		 *                    which items were removed
		 */
		protected void _fireTreeNodesRemoved(Object parent, int[] itemIndexes, Object[] items)
		{
			Object[] path = this.getPath(parent);
			TreeModelEvent event = new TreeModelEvent(this, path, itemIndexes, items);
			
			for (TreeModelListener listener : this._listeners)
				listener.treeNodesRemoved(event);
		}
		
		/**
		 * Fires a 'tree structure change' event for any
		 * interested listeners.
		 * 
		 * @param parent The node whose structure has been changed
		 */
		protected void _fireTreeStructureChanged(Object parent)
		{
			TreeModelEvent event = new TreeModelEvent(this, this.getPath(parent));
			
			for (TreeModelListener listener : this._listeners)
				listener.treeStructureChanged(event);
		}
		
		/**
		 * Fires a 'complete tree change' event for any
		 * interested listeners.
		 * 
		 * @param newRoot The new root node.
		 */
		protected void _fireCompleteTreeChange(Object newRoot)
		{
			TreeModelEvent event = new TreeModelEvent(this, new TreePath(newRoot));
			
			for (TreeModelListener listener : this._listeners)
				listener.treeStructureChanged(event);
		}
	}
	
	/**
	 * Class for a common transfer handler that enables drag-and-drop
	 * inside ArchiveGroupsView widgets.
	 */
	protected static class XferHandler extends TransferHandler
	{
		private static final long serialVersionUID = 1L;
		private static final Transferable _ERROR_TRANSFER = new StringSelection("Error");

		@Override
		public int getSourceActions(JComponent source)
		{
			return TransferHandler.MOVE;
		}
		
		@Override
		protected Transferable createTransferable(JComponent source)
		{
			ArchiveGroupsView agv = (ArchiveGroupsView)source;
			
			// Check whether the selection can be merged or moved
			if (!agv.canMoveSelection() && !agv.canMergeSelection()) return _ERROR_TRANSFER;
			
            return this._encodeItemList(agv, agv.getSelection());
        }
		
		@Override
		public void exportDone(JComponent source, Transferable data, int action)
		{
			ArchiveGroupsView agv = (ArchiveGroupsView)source;
			XferHandler.Operation op = agv._pendingDDOperation;
			
			if (action != TransferHandler.MOVE) return;
			
			if (op.type == Operation.Type.MERGE) { agv.doMergeItems(op.items, op.destination); }
			else if (op.type == Operation.Type.MOVE) { agv.doMoveItems(op.items, op.destination); }
		}

		@Override
		public boolean canImport(TransferHandler.TransferSupport dropInfo)
		{
			Operation op = this._comprehendOperation(dropInfo);
			
			return (op != null);
        }
		
		@Override
		public boolean importData(TransferHandler.TransferSupport dropInfo)
		{
			Operation op = this._comprehendOperation(dropInfo);
			if (op == null) return false;
			
			ArchiveGroupsView agv = (ArchiveGroupsView)(dropInfo.getComponent());
			agv._pendingDDOperation = op;
			
            return true;
        }
		
		/**
		 * Encodes an item-list transfer.
		 * 
		 * @param source The Archive Groups View control that the
		 *               selected items belong to
		 * @param items An list of selected items
		 * @return A Transferable object that encapsulates the item list
		 */
		protected Transferable _encodeItemList(ArchiveGroupsView source, List<Object> items)
		{
			StringBuilder buffer = new StringBuilder();
			
			// Add the source component's unique code
			buffer.append(source.controlUID+"\n");
			
			for (Object item : items)
			{
				if (item instanceof IMArchive) { buffer.append("archive\n"); }
				else if (item instanceof Group) { buffer.append("group:"+((Group)item).name+"\n"); }
				else if (item instanceof Contact) { buffer.append("contact:"+((Contact)item).name+"\n"); }
				else if (item instanceof Account) { buffer.append("account:"+((Account)item).service.shortName+":"+((Account)item).name+"\n"); }
			}
			
			return new StringSelection(buffer.toString());
		}
		
		/**
		 * Decodes an item-list transfer.
		 * 
		 * @param destination The Archive Groups View control that is
		 *                    receiving the items
		 * @param data The Transferable object that encapsulates the items
		 * @return A list containing the transfered items, or null if
		 *         the data is malformed or did not originate from the
		 *         same control
		 */
		protected List<Object> _decodeItemList(ArchiveGroupsView destination, Transferable data)
		{
			try
            {
            	String[] strings = ((String)data.getTransferData(DataFlavor.stringFlavor)).split("\n");
            	
            	// Reject transfers marked as erroneous from the source
            	if (strings[0].equals("Error")) return null;
            	
            	// Reject transfers not within the same component
            	int sourceUID = Integer.parseInt(strings[0]);
            	if (sourceUID != destination.controlUID) return null;
            	
            	// Read items
            	List<Object> items = new ArrayList<Object>();
            	IMArchive archive = destination.getArchive();
            	
            	for (int i=1; i<strings.length; i++)
            	{
            		String[] parts = strings[i].split(":",2);
            		
            		if (parts[0].equals("archive")) { items.add(archive); }
            		else if (parts[0].equals("group")) { items.add(archive.getGroupByName(parts[1])); }
            		else if (parts[0].equals("contact")) { items.add(archive.getContactByName(parts[1])); }
            		else if (parts[0].equals("account"))
            		{
            			String[] accParts = parts[1].split(":",2);
            			items.add(archive.getAccountByName(IMService.fromShortName(accParts[0]),accParts[1]));
            		}
            	}
            	
            	return items;
            }
			catch (Exception e)
            {
				return null;
            }
		}
		
		/**
		 * Analyzes the location and items in a drop operation and
		 * figures out the exact operation (merge, move, etc.) desired
		 * by the user, as well as the necessary parameters.
		 * 
		 * @param dropInfo A class describing the drop operation
		 * @return An Operation structure, or null if the operation is
		 *         in any way invalid
		 */
		protected Operation _comprehendOperation(TransferHandler.TransferSupport dropInfo)
		{
			if (!dropInfo.isDrop()) return null;
            if (!dropInfo.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;

            ArchiveGroupsView dest = (ArchiveGroupsView)(dropInfo.getComponent());
            
            List<Object> items = this._decodeItemList(dest, dropInfo.getTransferable());
            if ((items == null) || (items.isEmpty())) return null;
            
            JTree.DropLocation dl = (JTree.DropLocation)dropInfo.getDropLocation();
            boolean isInsert = (dl.getChildIndex() != -1);
            Object into = dl.getPath().getLastPathComponent();
            if (into == null) return null;
            
            ItemType itemType = dest.getItemType(items.get(0));
            ItemType targetType = dest.getItemType(into);
            
            // Check for "merge" operations
            if (!isInsert && (itemType == targetType) &&
            		((itemType == ItemType.GROUP) || (itemType == ItemType.CONTACT) || (itemType == ItemType.IDENTITY)))
            {
            	// Sanity check: can't merge anything into the Identities group
            	if ((targetType == ItemType.GROUP) && ((Group)into).isIdentitiesGroup()) return null;
            	
            	return new Operation(Operation.Type.MERGE, items, into);
            }
            
            // Check for "move" operations
            if (
            		((itemType == ItemType.GROUP) && (targetType == ItemType.ARCHIVE)) ||
            		((itemType == ItemType.CONTACT) && (targetType == ItemType.GROUP)) ||
            		((itemType == ItemType.ACCOUNT) && (targetType == ItemType.CONTACT) ||
            		((itemType == ItemType.IDENT_ACCOUNT) && (targetType == ItemType.IDENTITY))))
            {
            	// Sanity check: can't move anything into the Identities group
            	if ((targetType == ItemType.GROUP) && ((Group)into).isIdentitiesGroup()) return null;
            	
            	// Adjust the location for groups
            	if ((itemType == ItemType.GROUP) && isInsert)
            	{
            		List<Group> groups = dest.getArchive().getGroups();
            		if (dl.getChildIndex() < groups.size()) into = groups.get(dl.getChildIndex());
            		if (dl.getChildIndex() == 0) return null; // can't move before Identities group
            	}
            	
            	return new Operation(Operation.Type.MOVE, items, into);
            }
            
            return null;
        }
		
		/**
		 * Internal class for representing the result of the TransferHandler
		 * figuring out the exact operation desired by the user and its
		 * parameters.
		 */
		public static class Operation
		{
			public static enum Type { MOVE, MERGE };
			
			public final Type type;
			public final List<Object> items;
			public final Object destination;
			
			public Operation(Type type, List<Object> items, Object destination)
			{
				this.type = type;
				this.items = items;
				this.destination = destination;
			}
		}
	}
}
