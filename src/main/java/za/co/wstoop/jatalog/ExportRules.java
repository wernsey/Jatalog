package za.co.wstoop.jatalog;

import za.co.wstoop.jatalog.output.DefaultQueryOutput;
import za.co.wstoop.jatalog.output.OutputUtils;
import za.co.wstoop.jatalog.output.QueryOutput;

import java.io.*;
import java.util.*;

/**
 * Export rules for Jatalog.
 */
public class ExportRules {
    
    /**
     * Main method.
     */
    public static void main(String... args) {
        System.out.println(Arrays.toString(args));
    }

    private Collection<Rule> idb;


    public void exportToHtml(String inputFilename) {
        try {
            // On lit le fichier de regles et on execute
            Jatalog jatalog = new Jatalog();
            QueryOutput qo = new DefaultQueryOutput();
            try (Reader reader = new BufferedReader(new FileReader(inputFilename))) {
                jatalog.executeAll(reader, qo);
            }

            // On recupere les regles
            this.idb = jatalog.getIdb();

            // On genere le HTML et on l'ecrit dans un fichier
            try (PrintStream out = new PrintStream(new FileOutputStream(inputFilename + ".html"))) {
                out.print(this.generateHtml());
            }
        } catch (DatalogException | IOException e) {
            e.printStackTrace();
        }
    }

    private String generateHtml() {
        StringBuilder result = new StringBuilder();

        result.append(this.generateHtmlTop());
        result.append(this.generateHtmlBody());
        result.append(this.generateHtmlBottom());

        return result.toString();
    }

    private String generateHtmlTop() {
        StringBuilder result = new StringBuilder();

        result.append("<html>");
        result.append("<head>");
        result.append("<title>Jatalog - Export Rules</title>");
        result.append("<script>\n");
//code.iamkate.com
        result.append("var CollapsibleLists=function(){function e(b,c){[].forEach.call(b.getElementsByTagName(\"li\"),function(a){c&&b!==a.parentNode||(a.style.userSelect=\"none\",a.style.MozUserSelect=\"none\",a.style.msUserSelect=\"none\",a.style.WebkitUserSelect=\"none\",a.addEventListener(\"click\",g.bind(null,a)),f(a))})}function g(b,c){for(var a=c.target;\"LI\"!==a.nodeName;)a=a.parentNode;a===b&&f(b)}function f(b){var c=b.classList.contains(\"collapsibleListClosed\"),a=b.getElementsByTagName(\"ul\");[].forEach.call(a,function(a){for(var d=a;\"LI\"!==d.nodeName;)d=d.parentNode;d===b&&(a.style.display=c?\"block\":\"none\")});b.classList.remove(\"collapsibleListOpen\");b.classList.remove(\"collapsibleListClosed\");0<a.length&&b.classList.add(\"collapsibleList\"+(c?\"Open\":\"Closed\"))}return{apply:function(b){[].forEach.call(document.getElementsByTagName(\"ul\"),function(c){c.classList.contains(\"collapsibleList\")&&(e(c,!0),b||[].forEach.call(c.getElementsByTagName(\"ul\"),function(a){a.classList.add(\"collapsibleList\")}))})},applyTo:e}}();\n");
//code.iamkate.com
//        result.append("var runOnLoad=function(c,o,d,e){function x(){for(e=1;c.length;)c.shift()()}o[d]?(document[d]('DOMContentLoaded',x,0),o[d]('load',x,0)):o.attachEvent('onload',x);return function(t){e?o.setTimeout(t,0):c.push(t)}}([],window,'addEventListener');");
        // make the appropriate lists collapsible
//        result.append("runOnLoad(CollapsibleLists.apply());");
        result.append("window.onload = function(){CollapsibleLists.apply()}\n");
        result.append("</script>\n");

        result.append("</head>\n");
        result.append("<body>\n");
        result.append("<h1>Jatalog - Export Rules</h1>\n");
        result.append("<div>\n");


        return result.toString();
    }

    private String generateHtmlBottom() {
        StringBuilder result = new StringBuilder();

        result.append("</div>");
        result.append("</body>");
        result.append("</html>");

        return result.toString();
    }

    private String generateHtmlBody() {
        StringBuilder result = new StringBuilder();
        // On recherche les regles parentes (celles qui ne sont utilisées par aucune autre regle)
        for (Rule rule : this.idb) {
            if (isParentRule(rule)) {
                System.out.println("Export rule: " + rule.getHead());
                result.append(displayRule(rule));
            }
        }
        return result.toString();
    }

    private boolean isParentRule(Rule rule) {
        final Expr head = rule.getHead();
        for (Rule otherRule : this.idb) {
            for (Expr bodyExpr : otherRule.getBody()) {
                if (bodyExpr.getPredicate().equals(head.getPredicate())) {
                    return false;
                }
            }
        }
        return true;
    }

    private String displayRule(Rule rule) {
        return displayRule(rule, 0);
    }

    private String displayRule(Rule rule, int level) {
        StringBuilder result = new StringBuilder();

        Expr head = rule.getHead();
        result.append("<ul class=\"collapsibleList\">");
        result.append("<li>");
        result.append(head);
        result.append(" :-\n");

        List<Expr> body = rule.getBody();
        result.append("<ul>");
        for (Expr expr : body) {
            if (this.hasRule(expr.getPredicate())) {
                // The current body expression is also a rule
                // Append all rules
                for (Rule otherRule : this.idb) {
                    if (otherRule.getHead().getPredicate().equals(expr.getPredicate())) {
                        result.append(this.displayRule(otherRule, level+1));
                    }
                }
            } else {
                // The current body expression is not a rule so just display it
                result.append(this.displayExpr(expr));
            }
        }
        result.append("</ul>");
        result.append("</li>\n");

        result.append("</ul>\n");

        return result.toString();
    }

    private boolean hasRule(String predicate) {
        for (Rule otherRule : this.idb) {
            if (otherRule.getHead().getPredicate().equals(predicate)) {
                return true;
            }
        }
        return false;
    }

    private String displayExpr(Expr expr) {
        StringBuilder result = new StringBuilder();

        result.append("<li>");
        result.append(expr);
        result.append("</li>\n");

        return result.toString();
    }

}
