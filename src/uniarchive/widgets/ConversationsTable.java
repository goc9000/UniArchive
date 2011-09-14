/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import uniarchive.graphics.IconManager;
import uniarchive.models.archive.ConversationsQuery;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchiveEvent;
import uniarchive.models.archive.IMArchiveListener;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;

/**
 * Class for a tabular view of conversations in an archive.
 */
public class ConversationsTable extends TableWithFillColumn
{
	private static final long serialVersionUID = 1L;
	
	/**
	 * Constructor.
	 */
	public ConversationsTable()
	{
		super();
		
		Model model = new Model();
				
		// Configure table properties
		this.setAutoCreateColumnsFromModel(false);
		this.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		this.setShowGrid(false);
		this.setIntercellSpacing(new Dimension(0,0));
		this.setModel(model);
		// KLUDGE: this makes the table look more decent under some Linux themes
		this.setBackground(UIManager.getColor("TextPane.background"));
		
		// Create columns
		TableColumn typeColumn = new TableColumn(Model.COLUMN_INDEX_IS_CONF);
		typeColumn.setPreferredWidth(this.getFont().getSize()*3);
		typeColumn.setCellRenderer(new TypeRenderer());
		
		TableColumn dateColumn = new TableColumn(Model.COLUMN_INDEX_DATE);
		dateColumn.setPreferredWidth(this.getFont().getSize()*10);
		dateColumn.setCellRenderer(new DateRenderer());
		
		TableColumn selfColumn = new TableColumn(Model.COLUMN_INDEX_SELF);
		selfColumn.setPreferredWidth(this.getFont().getSize()*12);
		selfColumn.setCellRenderer(new ContactRenderer());
		
		TableColumn withColumn = new TableColumn(Model.COLUMN_INDEX_WITH);
		withColumn.setPreferredWidth(this.getFont().getSize()*12);
		withColumn.setCellRenderer(new ContactRenderer());
		
		// Add columns
		this.addColumn(typeColumn);
		this.addColumn(dateColumn);
		// this.addColumn(selfColumn); // hidden by default
		this.addColumn(withColumn);
		this.setFillColumn(withColumn);
		
		this.getTableHeader().addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				JTableHeader header = (JTableHeader)e.getComponent();
				
				int colIdx = header.getColumnModel().getColumnIndexAtX(e.getX());
				int colModelIdx = header.getColumnModel().getColumn(colIdx).getModelIndex();
				
				if (colModelIdx >= 0) _onClickColumnHeader(colModelIdx);
			}
		});
	}
	
	/**
	 * Sets the archive viewed by this table.
	 * 
	 * @param archive The new archive object that is to
	 *                be viewed.
	 */
	public void setArchive(IMArchive archive)
	{
		this._model().setArchive(archive);
	}
	
	/**
	 * Gets the archive viewed by this table.
	 * 
	 * @return The currently viewed archive object.
	 */
	public IMArchive getArchive()
	{
		return this._model().getArchive();
	}
	
	/**
	 * Selects the first conversation in the table.
	 */
	public void selectFirst()
	{
		this.getSelectionModel().setSelectionInterval(0, 0);
	}
	
	/**
	 * Gets the current selection.
	 * 
	 * @return An array of selected conversations.
	 */
	public Conversation[] getSelection()
	{
		int[] indices = this.getSelectedRows();
		Conversation[] selection = new Conversation[this.getSelectedRowCount()];
		
		for (int i=0; i<this.getSelectedRowCount(); i++)
		{
			selection[i] = this._model().getRecord(this.convertColumnIndexToModel(indices[i]));
		}
		
		return selection;
	}
	
	/**
	 * Gets the first selected conversation.
	 * 
	 * @return A conversation object, or null if none is selected.
	 */
	public Conversation getSelectedConversation()
	{
		int index = this.getSelectedRow();
		
		return (index == -1) ? null : this._model().getRecord(index);
	}
	
	/**
	 * Sets the query used by this table to fetch conversations.
	 * 
	 * @param query A conversations query
	 */
	public void setQuery(ConversationsQuery query)
	{
		this._model().setQuery(query);
	}
	
	/**
	 * Gets the query used by this table to fetch conversations.
	 * 
	 * @return A conversations query
	 */
	public ConversationsQuery getQuery()
	{
		return this._model().getQuery();
	}
	
	/**
	 * Returns this table's model, as a Model object.
	 * 
	 * @return A Model object
	 */
	protected Model _model()
	{
		return (Model)this.getModel();
	}
	
	/**
	 * Reacts to the user clicking on a column in order
	 * to sort conversations.
	 * 
	 * @param colModelIdx The index of the column, relative
	 *                    to the model
	 */
	protected void _onClickColumnHeader(int colModelIdx)
	{
		ConversationsQuery.SortKey sortKey;
		
		switch (colModelIdx)
		{
		case Model.COLUMN_INDEX_DATE: sortKey = ConversationsQuery.SortKey.BY_DATE; break;
		case Model.COLUMN_INDEX_WITH: sortKey = ConversationsQuery.SortKey.BY_CONTACT; break;
		case Model.COLUMN_INDEX_IS_CONF: sortKey = ConversationsQuery.SortKey.BY_TYPE; break;
		default: return;
		}
		
		ConversationsQuery query = this._model().getQuery();
		query.sortKeys.remove(sortKey);
		query.sortKeys.add(0, sortKey);
		this._model().setQuery(query);
	}
	
	/**
	 * Internal class for rendering a Contact/Account/Identity
	 * cell in this data table. 
	 */
	protected static class ContactRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			String text = "";
			ImageIcon icon = null;
			
			if (value instanceof Account)
			{
				text = ((Account)value).name;
				icon = ((Account)value).service.icon;
			}
			else if (value instanceof Contact)
			{
				text = ((Contact)value).name;
			}
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, text, isSelected, hasFocus,
					row, column);
			
            renderer.setIcon(icon);
			renderer.setIconTextGap(6);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class for rendering a Conversation Type cell
	 * in this data table. 
	 */
	protected static class TypeRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		protected ImageIcon _chatIcon = IconManager.getInstance().getIcon("conversation");
		protected ImageIcon _conferenceIcon = IconManager.getInstance().getIcon("conference");
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, null, isSelected, hasFocus,
					row, column);
			
			renderer.setHorizontalAlignment(JLabel.CENTER);
			renderer.setIcon(((Boolean)value).booleanValue() ? _conferenceIcon : _chatIcon);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class for rendering a Date cell
	 * in this data table. 
	 */
	protected static class DateRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			final SimpleDateFormat format = new SimpleDateFormat("dd MMM ''yy  HH:mm");
			
			Date date = (Date)value;
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, format.format(date),
					isSelected, hasFocus, row, column);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class defining the model for the Conversations Table.
	 */
	protected static class Model extends AbstractTableModel implements IMArchiveListener
	{
		private static final long serialVersionUID = 1L;
		
		public static final int COLUMN_INDEX_DATE = 0;
		public static final int COLUMN_INDEX_SELF = 1;
		public static final int COLUMN_INDEX_WITH = 2;
		public static final int COLUMN_INDEX_IS_CONF = 3;
		
		protected IMArchive _archive = null;
		protected ConversationsQuery _query = new ConversationsQuery();
		protected List<Conversation> _convList = null;
		protected int _cachedConvListSize = 0;
		
		/**
		 * Constructor.
		 */
		public Model()
		{
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
			this.setQuery(new ConversationsQuery());
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
		
		/**
		 * Sets the query used by this model to fetch conversations.
		 * 
		 * @param query A conversations query
		 */
		public void setQuery(ConversationsQuery query)
		{
			this._query = query;
			this.requery();
		}
		
		/**
		 * Gets the query used by this model to fetch conversations.
		 * 
		 * @return A conversations query
		 */
		public ConversationsQuery getQuery()
		{
			return this._query;
		}
		
		/**
		 * Updates the data in the table after a change in
		 * the archive, query, or underlying data.
		 */
		public void requery()
		{
			this._convList = (this._archive != null) ? this._archive.getConversations(this._query) : new ArrayList<Conversation>();
			this._cachedConvListSize = this._convList.size();
			
			this.fireTableDataChanged();
		}
		
		@Override
		public int getColumnCount()
		{
			return 4;
		}

		@Override
		public int findColumn(String columnName)
		{
			return super.findColumn(columnName);
		}

		@Override
		public Class<?> getColumnClass(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_DATE: return Date.class;
				case COLUMN_INDEX_SELF: return Contact.class;
				case COLUMN_INDEX_WITH: return Contact.class;
				case COLUMN_INDEX_IS_CONF: return Boolean.class;
				default: return null;
			}
		}

		@Override
		public String getColumnName(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_DATE: return "Date";
				case COLUMN_INDEX_SELF: return "Id";
				case COLUMN_INDEX_WITH: return "With";
				case COLUMN_INDEX_IS_CONF: return "";
				default: return "N/A";
			}
		}

		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}

		@Override
		public int getRowCount()
		{
			return this._cachedConvListSize;
		}
		
		/**
		 * Returns the record at a given index.
		 * 
		 * @param index A zero-based record index
		 * @return A Conversation record
		 */
		public Conversation getRecord(int index)
		{	
			if (this._convList == null) return null;
				
			return this._convList.get(index);
		}
		
		@Override
		public Object getValueAt(int row, int column)
		{
			Conversation record = this.getRecord(row);
			
			switch(column)
			{
				case COLUMN_INDEX_DATE: return record.dateStarted;
				case COLUMN_INDEX_SELF: return record.localAccount.getContact();
				case COLUMN_INDEX_WITH: return record.remoteAccount.getContact();
				case COLUMN_INDEX_IS_CONF: return Boolean.valueOf(record.isConference);
				default: return null;
			}
		}
		
		/**
		 * Reacts to an event in the archive queried by this
		 * control.
		 * 
		 * @param event An event describing the change that
		 *              has occured.
		 */
		public void archiveChanged(IMArchiveEvent event)
		{
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
			case UPDATED_CONVERSATIONS:
			case MAJOR_CHANGE:
				this.requery();
			}
		}
	}
}

