package foreman.ui;

import foreman.app.ForemanSettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HelpPanel extends JPanel {

    private record Topic(String title, String slug) {}
    private record Section(String title, List<Topic> topics) {}

    private static final List<Section> SECTIONS = List.of(
        new Section("Tutorial", List.of(
            new Topic("Getting started", "getting-started")
        )),
        new Section("Projects", List.of(
            new Topic("Registering a project",  "register-project"),
            new Topic("Role discovery",          "role-discovery"),
            new Topic("Sidecar projects",        "sidecar-projects")
        )),
        new Section("Sessions", List.of(
            new Topic("What is a session?", "what-is-a-session"),
            new Topic("Launch",             "launch"),
            new Topic("Focus",              "focus"),
            new Topic("Brief",              "brief")
        )),
        new Section("Briefings", List.of(
            new Topic("What's in a briefing",      "briefing-contents"),
            new Topic("GitHub issues integration", "github-issues")
        )),
        new Section("App", List.of(
            new Topic("Settings",           "settings"),
            new Topic("Keyboard shortcuts", "keyboard-shortcuts")
        ))
    );

    private static final String CSS =
        "body{font-family:sans-serif;font-size:13px;line-height:1.5;margin:12px 16px;}" +
        "h1{font-size:16px;margin-top:0;}" +
        "h2{font-size:13px;margin-top:14px;}" +
        "pre{background:#f4f4f4;padding:8px;border-radius:4px;font-size:12px;}" +
        "table{border-spacing:8px 2px;}" +
        "td:first-child{white-space:nowrap;}" +
        "a{color:#2d7dd2;}" +
        "kbd{font-family:monospace;font-size:11px;border:1px solid #aaa;border-radius:3px;padding:1px 4px;}";

    private final JEditorPane  articlePane;
    private final JPanel       topicListPanel;
    private final boolean      popOutEnabled;
    private String             currentSlug = "getting-started";
    private JLabel             selectedLabel;

    public HelpPanel(ForemanSettingsService settingsService, boolean popOutEnabled) {
        super(new BorderLayout());
        this.popOutEnabled = popOutEnabled;

        articlePane = new JEditorPane();
        articlePane.setEditable(false);
        articlePane.setContentType("text/html");
        articlePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        articlePane.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            var url = e.getURL();
            var desc = e.getDescription();
            if (desc != null && desc.startsWith("concept://")) {
                loadArticle(desc.substring("concept://".length()));
            } else if (url != null) {
                try {
                    Desktop.getDesktop().browse(url.toURI());
                } catch (IOException | URISyntaxException ignored) {}
            }
        });

        topicListPanel = new JPanel();
        topicListPanel.setLayout(new BoxLayout(topicListPanel, BoxLayout.Y_AXIS));
        topicListPanel.setBorder(new EmptyBorder(8, 0, 8, 0));
        buildTopicList();

        var topicScroll = new JScrollPane(topicListPanel);
        topicScroll.setBorder(null);
        topicScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        var articleScroll = new JScrollPane(articlePane);
        articleScroll.setBorder(null);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, topicScroll, articleScroll);
        split.setDividerLocation(180);
        split.setDividerSize(4);

        add(split, BorderLayout.CENTER);

        if (popOutEnabled) {
            var popOutBtn = new JButton("Pop out ⤢");
            popOutBtn.setToolTipText("Open help in a floating window");
            popOutBtn.addActionListener(e -> openPopOut(settingsService));
            var bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            bar.add(popOutBtn);
            add(bar, BorderLayout.NORTH);
        }

        loadArticle(currentSlug);
    }

    public void showTopic(String slug) {
        loadArticle(slug);
    }

    private void buildTopicList() {
        topicListPanel.removeAll();
        for (var section : SECTIONS) {
            var header = new JLabel(section.title().toUpperCase());
            header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
            header.setForeground(UIManager.getColor("Label.disabledForeground"));
            header.setBorder(new EmptyBorder(10, 12, 2, 12));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            topicListPanel.add(header);

            for (var topic : section.topics()) {
                var lbl = new JLabel(topic.title());
                lbl.setBorder(new EmptyBorder(3, 20, 3, 12));
                lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                lbl.setOpaque(true);

                if (topic.slug().equals(currentSlug)) {
                    lbl.setBackground(UIManager.getColor("List.selectionBackground"));
                    lbl.setForeground(UIManager.getColor("List.selectionForeground"));
                    selectedLabel = lbl;
                } else {
                    lbl.setBackground(UIManager.getColor("Panel.background"));
                    lbl.setForeground(UIManager.getColor("Label.foreground"));
                }

                lbl.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        loadArticle(topic.slug());
                    }
                });

                var wrapper = new JPanel(new BorderLayout());
                wrapper.setOpaque(false);
                wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
                wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, lbl.getPreferredSize().height + 6));
                wrapper.add(lbl, BorderLayout.CENTER);
                topicListPanel.add(wrapper);
            }
        }
        topicListPanel.revalidate();
        topicListPanel.repaint();
    }

    private void loadArticle(String slug) {
        currentSlug = slug;
        buildTopicList();
        var resourcePath = "/resources/help/" + slug + ".html";
        var url = HelpPanel.class.getResource(resourcePath);
        if (url == null) {
            articlePane.setText("<html><body style=\"" + CSS + "\"><p>Article not found: " + slug + "</p></body></html>");
            return;
        }
        try (var in = url.openStream()) {
            var body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            articlePane.setText("<html><head><style>" + CSS + "</style></head><body>" + body + "</body></html>");
            articlePane.setCaretPosition(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void openPopOut(ForemanSettingsService settingsService) {
        var owner = (Frame) SwingUtilities.getWindowAncestor(this);
        var dialog = new JDialog(owner, "Foreman Help", false);
        var panel = new HelpPanel(settingsService, false);
        panel.loadArticle(currentSlug);
        dialog.setContentPane(panel);

        var s = settingsService.get();
        if (s.helpDialogX() != null) {
            dialog.setBounds(s.helpDialogX(), s.helpDialogY(), s.helpDialogWidth(), s.helpDialogHeight());
        } else {
            dialog.setSize(800, 600);
            dialog.setLocationRelativeTo(owner);
        }

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                var b = dialog.getBounds();
                settingsService.update(settingsService.get().withHelpDialogBounds(b.x, b.y, b.width, b.height));
            }
        });

        dialog.setVisible(true);
    }
}
