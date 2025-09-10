/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.treeui;

import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import jsystem.treeui.tree.AssetNode;
import jsystem.treeui.tree.GroupsManager;
import jsystem.treeui.tree.RootNode;
import jsystem.utils.PerformanceUtil;

/**
 * User: michaelo Date: Dec 10, 2004 Time: 2:12:35 PM
 */
public class TestTreeModel implements TreeModel {

	private static Logger log = LoggerFactory.getLogger(TestTreeModel.class);

	private AssetNode root = null;

	public TestTreeModel() {

		try {
			AssetNode.initFailLoadClassVector();
			GroupsManager.getInstance().reset();
			int index = PerformanceUtil.startMeasure();
			root = new RootNode();
			PerformanceUtil.endMeasure(index, "Loading all assets tree");
			root.cleanLeafsWithoutTests();
		} catch (Exception e) {
			log.warn("Unable to create a root node for TestTreeModel", e);
		}
	}

	public Object getRoot() {
		return root;
	}

	public int getChildCount(Object parent) {
		return ((AssetNode) parent).getChildCount();
	}

	public boolean isLeaf(Object node) {
		return ((AssetNode) node).isLeaf();
	}

	public void addTreeModelListener(TreeModelListener l) {
	}

	public void removeTreeModelListener(TreeModelListener l) {
	}

	public Object getChild(Object parent, int index) {
		return ((AssetNode) parent).getChildAt(index);
	}

	public int getIndexOfChild(Object parent, Object child) {
		if (parent != null) {
			int i = 0;

			for (Enumeration<?> e = ((AssetNode) parent).children(); e.hasMoreElements(); i++) {
				if (e.nextElement().equals(child)) {
					return i;
				}
			}
		}
		return -1;
	}

	public void valueForPathChanged(TreePath path, Object newValue) {
	}
}
