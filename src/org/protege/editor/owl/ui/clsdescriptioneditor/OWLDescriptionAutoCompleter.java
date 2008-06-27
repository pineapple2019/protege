package org.protege.editor.owl.ui.clsdescriptioneditor;

import org.apache.log4j.Logger;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.description.OWLExpressionParserException;
import org.semanticweb.owl.model.OWLEntity;
import org.semanticweb.owl.model.OWLException;
import org.semanticweb.owl.model.OWLObject;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: May 4, 2006<br><br>
 * <p/>
 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class OWLDescriptionAutoCompleter {

    private static Logger logger = Logger.getLogger(OWLDescriptionAutoCompleter.class);

    public static final int DEFAULT_MAX_ENTRIES = 100;

    private OWLEditorKit owlEditorKit;

    private JTextComponent textComponent;

    private KeyListener keyListener;

    private Set<String> wordDelimeters;

    private AutoCompleterMatcher matcher;

    private JList popupList;

    private JWindow popupWindow;

    public static final int POPUP_WIDTH = 350;

    public static final int POPUP_HEIGHT = 300;

    private OWLExpressionChecker checker;

    private String lastTextUpdate = "*";

    private int maxEntries = DEFAULT_MAX_ENTRIES;


    public OWLDescriptionAutoCompleter(OWLEditorKit owlEditorKit, JTextComponent tc,
                                       OWLExpressionChecker checker) {
        this.owlEditorKit = owlEditorKit;
        this.checker = checker;
        this.textComponent = tc;
        keyListener = new KeyAdapter() {

            public void keyPressed(KeyEvent e) {
                processKeyPressed(e);
            }


            public void keyReleased(KeyEvent e) {

                if (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN) {
                    if (popupWindow.isVisible() && !lastTextUpdate.equals(textComponent.getText())) {
                        lastTextUpdate = textComponent.getText();
                        updatePopup(getMatches());
                    }
                }
            }
        };
        textComponent.addKeyListener(keyListener);

        wordDelimeters = new HashSet<String>();
        wordDelimeters.add(" ");
        wordDelimeters.add("\n");
        wordDelimeters.add("[");
        wordDelimeters.add("]");
        wordDelimeters.add("{");
        wordDelimeters.add("}");
        wordDelimeters.add("(");
        wordDelimeters.add(")");
        wordDelimeters.add(",");
        wordDelimeters.add("^");
        matcher = new AutoCompleterMatcherImpl(owlEditorKit.getOWLModelManager());
        popupList = new JList();
        popupList.setAutoscrolls(true);
        popupList.setCellRenderer(owlEditorKit.getOWLWorkspace().createOWLCellRenderer());
        popupList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    completeWithPopupSelection();
                }
            }
        });
        popupList.setRequestFocusEnabled(false);
        createPopupWindow();
        textComponent.addHierarchyListener(new HierarchyListener() {
            /**
             * Called when the hierarchy has been changed. To discern the actual
             * type of change, call <code>HierarchyEvent.getChangeFlags()</code>.
             * @see java.awt.event.HierarchyEvent#getChangeFlags()
             */
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                    createPopupWindow();
                }
            }
        });
    }


    private void processKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown()) {
            // Show popup
            performAutoCompletion();
        }
        else if (e.getKeyCode() == KeyEvent.VK_TAB) {
            e.consume();
            performAutoCompletion();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (popupWindow.isVisible()) {
                // Hide popup
                e.consume();
                hidePopup();
            }
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (popupWindow.isVisible()) {
                // Complete
                e.consume();
                completeWithPopupSelection();
            }
        }
        else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            if (popupWindow.isVisible()) {
                e.consume();
                incrementSelection();
            }
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP) {
            if (popupWindow.isVisible()) {
                e.consume();
                decrementSelection();
            }
        }
        else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            hidePopup();
        }
        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            hidePopup();
        }
    }


    private void completeWithPopupSelection() {
        if (popupWindow.isVisible()) {
            Object selObject = popupList.getSelectedValue();
            if (selObject != null) {
                if (selObject instanceof OWLEntity) {
                    insertWord(getInsertText(selObject));
                }
                else {
                    insertWord(getInsertText(selObject));
                }
                hidePopup();
            }
        }
    }


    private List getMatches() {
        // We need to determine if the matches should be classes, individuals etc.

        int wordIndex = getWordIndex();
        if (wordIndex > -1) {
            try {
                String expression = textComponent.getDocument().getText(0, wordIndex);
                // Add a bit to the end to force a parse error
                expression += "**";
                try {
                    checker.check(expression);
                }
                catch (OWLExpressionParserException e) {
                    String word = getWordToComplete();
                    Set<OWLObject> matches = matcher.getMatches(word,
                                                     e.isOWLClassExpected(),
                                                     e.isOWLObjectPropertyExpected(),
                                                     e.isOWLDataPropertyExpected(),
                                                     e.isOWLIndividualExpected(),
                                                     e.isDataTypeExpected());
                    List kwMatches = new ArrayList(matches.size() + 10);
                    for (String s : e.getExpectedKeyWords()) {
                        if (s.toLowerCase().startsWith(word.toLowerCase())) {
                            kwMatches.add(s);
                        }
                    }
                    kwMatches.addAll(matches);

                    return kwMatches;
                }
                catch (OWLException owlEx) {
                    owlEx.printStackTrace();
                }
            }
            catch (BadLocationException e) {
                Logger.getLogger(getClass()).warn(e);
            }
        }
        return Collections.EMPTY_LIST;
    }


    private void createPopupWindow() {
        JScrollPane sp = ComponentFactory.createScrollPane(popupList);
        popupWindow = new JWindow((Window) SwingUtilities.getAncestorOfClass(Window.class, textComponent));
        popupWindow.setAlwaysOnTop(true);
        popupWindow.getContentPane().setLayout(new BorderLayout());
        popupWindow.getContentPane().add(sp, BorderLayout.CENTER);
        popupWindow.setFocusableWindowState(false);
    }


    private void performAutoCompletion() {
        List matches = getMatches();
        if (matches.size() == 1) {
            // Don't show popup
            insertWord(getInsertText(matches.iterator().next()));
        }
        else if (matches.size() > 1) {
            // Show popup
            lastTextUpdate = textComponent.getText();
            showPopup();
            updatePopup(matches);
        }
    }


    private void insertWord(String word) {
        try {
            int index = getWordIndex();
            int caretIndex = textComponent.getCaretPosition();
            textComponent.getDocument().remove(index, caretIndex - index);
            textComponent.getDocument().insertString(index, word, null);
        }
        catch (BadLocationException e) {
            logger.error(e);
        }
    }


    private void showPopup() {
        if (popupWindow == null) {
            createPopupWindow();
        }
        if (!popupWindow.isVisible()) {
            popupWindow.setSize(POPUP_WIDTH, POPUP_HEIGHT);
            try {
                int wordIndex = getWordIndex();
                if (wordIndex < 0) {
                    return;
                }
                Point p = textComponent.modelToView(getWordIndex()).getLocation();
                SwingUtilities.convertPointToScreen(p, textComponent);
                p.y = p.y + textComponent.getFontMetrics(textComponent.getFont()).getHeight();
                popupWindow.setLocation(p);
            }
            catch (BadLocationException e) {
                e.printStackTrace();
            }
            popupWindow.setVisible(true);
        }
    }


    private void hidePopup() {
        popupWindow.setVisible(false);
        popupList.setListData(new Object [0]);
    }


    private void updatePopup(List matches) {
        int count = matches.size();
        if(count > maxEntries) {
            count = maxEntries;
        }
        if (!matches.isEmpty()) {
            popupList.setListData(matches.subList(0, count).toArray());
        }
        else {
            popupList.setListData(matches.toArray());
        }
        popupList.setSelectedIndex(0);

        popupWindow.setSize(POPUP_WIDTH, POPUP_HEIGHT);
    }


    private void incrementSelection() {
        if (popupList.getModel().getSize() > 0) {
            int selIndex = popupList.getSelectedIndex();
            selIndex++;
            if (selIndex > popupList.getModel().getSize() - 1) {
                selIndex = 0;
            }
            popupList.setSelectedIndex(selIndex);
            popupList.scrollRectToVisible(popupList.getCellBounds(selIndex, selIndex));
        }
    }


    private void decrementSelection() {
        if (popupList.getModel().getSize() > 0) {
            int selIndex = popupList.getSelectedIndex();
            selIndex--;
            if (selIndex < 0) {
                selIndex = popupList.getModel().getSize() - 1;
            }
            popupList.setSelectedIndex(selIndex);
            popupList.scrollRectToVisible(popupList.getCellBounds(selIndex, selIndex));
        }
    }


    private int getWordIndex() {
        try {
            int caretPos = textComponent.getCaretPosition() - 1;
            for (int index = caretPos; index > -1; index--) {
                if (wordDelimeters.contains(textComponent.getDocument().getText(index, 1))) {
                    return index + 1;
                }
                if (index == 0) {
                    return 0;
                }
            }
        }
        catch (BadLocationException e) {
            logger.error(e);
        }
        return -1;
    }


    private String getInsertText(Object o) {
        if (o instanceof OWLObject) {
            OWLModelManager mngr = owlEditorKit.getOWLModelManager();
            return mngr.getRendering((OWLObject) o);
        }
        else {
            return o.toString();
        }
    }


    private String getWordToComplete() {
        try {
            int index = getWordIndex();
            int caretIndex = textComponent.getCaretPosition();
            return textComponent.getDocument().getText(index, caretIndex - index);
        }
        catch (BadLocationException e) {
            return "";
        }
    }


    public void uninstall() {
        textComponent.removeKeyListener(keyListener);
    }
}
