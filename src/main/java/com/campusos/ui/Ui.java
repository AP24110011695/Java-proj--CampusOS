package com.campusos.ui;

import com.campusos.model.CountStat;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Small factory for the shared visual vocabulary: cards, chips, badges. */
public final class Ui {

    private Ui() { }

    /** Dashboard statistic card: big number + caption. */
    public static VBox statCard(String value, String caption, String accentClass) {
        Label number = new Label(value);
        number.getStyleClass().addAll("stat-value", accentClass);
        Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        label.setWrapText(true);
        VBox card = new VBox(4, number, label);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(180);
        return card;
    }

    /** Status pill used inside table cells. */
    public static Label badge(String text, String colorClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", colorClass);
        return badge;
    }

    /** Standard status badge class for any enum constant name. */
    public static String badgeClass(Enum<?> status) {
        return "badge-" + status.name().toLowerCase().replace('_', '-');
    }

    /** KPI row card used on faculty/student dashboards. */
    public static HBox kpiRow(VBox... cards) {
        HBox row = new HBox(14, cards);
        row.getStyleClass().add("kpi-row");
        return row;
    }

    public static VBox card(String title, javafx.scene.Node content) {
        VBox card = new VBox(10, new Label(title), content);
        Label titleLabel = (Label) card.getChildren().get(0);
        titleLabel.getStyleClass().add("card-title");
        card.getStyleClass().add("card");
        VBox.setMargin(content, new Insets(0, 4, 4, 4));
        return card;
    }

    /** Empty-data placeholder label. */
    public static Label emptyHint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-hint");
        return label;
    }

    /** Formats a CountStat list for chart tooltips/labels. */
    public static String summarize(java.util.List<CountStat> stats) {
        StringBuilder sb = new StringBuilder();
        for (CountStat stat : stats) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(stat.getLabel()).append(": ").append(stat.getValue());
        }
        return sb.toString();
    }
}
