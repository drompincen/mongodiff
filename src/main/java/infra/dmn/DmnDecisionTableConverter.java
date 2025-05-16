package infra.dmn;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class DmnDecisionTableConverter {

    // Change this if your DMN uses a different namespace/version
    private static final String DMN_NS = "https://www.omg.org/spec/DMN/20191111/MODEL/";

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/main/java/infra/dmn/example.xml","./src/main/java/infra/dmn"};
        if (args.length < 2) {
            System.err.println("Usage: java DmnDecisionTableConverter <input.dmn> <output-directory>");
            System.exit(1);
        }

        File dmnFile = new File(args[0]);
        File outDir = new File(args[1]);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new RuntimeException("Cannot create output directory: " + outDir);
        }

        // 1. Parse DMN XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(dmnFile);

        // 2. Find all <decision> elements
        NodeList decisions = doc.getElementsByTagNameNS(DMN_NS, "decision");
        for (int i = 0; i < decisions.getLength(); i++) {
            Element decision = (Element) decisions.item(i);

            // 3. Locate the first <decisionTable> inside this decision
            NodeList dtList = decision.getElementsByTagNameNS(DMN_NS, "decisionTable");
            if (dtList.getLength() == 0) continue;
            Element dt = (Element) dtList.item(0);

            String tableId = dt.getAttribute("id");
            if (tableId == null || tableId.isEmpty()) {
                tableId = decision.getAttribute("id");
            }

            // 4. Extract input column names
            List<String> inputNames = new ArrayList<>();
            NodeList inputs = dt.getElementsByTagNameNS(DMN_NS, "input");
            for (int j = 0; j < inputs.getLength(); j++) {
                Element inp = (Element) inputs.item(j);

                // Try <label> first
                NodeList labels = inp.getElementsByTagNameNS(DMN_NS, "label");
                String name = null;
                if (labels.getLength() > 0) {
                    name = labels.item(0).getTextContent().trim();
                } else {
                    // Fallback to the expression text
                    NodeList exps = inp.getElementsByTagNameNS(DMN_NS, "inputExpression");
                    if (exps.getLength() > 0) {
                        Element ie = (Element) exps.item(0);
                        NodeList texts = ie.getElementsByTagNameNS(DMN_NS, "text");
                        if (texts.getLength() > 0) {
                            name = texts.item(0).getTextContent().trim();
                        }
                    }
                }
                inputNames.add(name != null ? name : ("Input" + (j+1)));
            }

            // 5. Extract output column names
            List<String> outputNames = new ArrayList<>();
            NodeList outputs = dt.getElementsByTagNameNS(DMN_NS, "output");
            for (int j = 0; j < outputs.getLength(); j++) {
                Element out = (Element) outputs.item(j);
                String oname = out.getAttribute("name");
                outputNames.add(oname != null && !oname.isEmpty() ? oname : ("Output" + (j+1)));
            }

            // 6. Prepare CSV
            File csv = new File(outDir, "DecisionTable_" + tableId + ".csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(csv))) {
                // Header row
                List<String> header = new ArrayList<>();
                header.add("Rule");
                inputNames.forEach(n -> header.add("CONDITION–" + n));
                outputNames.forEach(n -> header.add("ACTION–" + n));
                pw.println(joinCsv(header));

                // 7. Iterate rules
                NodeList rules = dt.getElementsByTagNameNS(DMN_NS, "rule");
                for (int r = 0; r < rules.getLength(); r++) {
                    Element rule = (Element) rules.item(r);

                    // inputs
                    NodeList inEntries = rule.getElementsByTagNameNS(DMN_NS, "inputEntry");
                    List<String> row = new ArrayList<>();
                    row.add(String.valueOf(r+1));
                    for (int k = 0; k < inputNames.size(); k++) {
                        String val = "";
                        if (k < inEntries.getLength()) {
                            Element ie = (Element) inEntries.item(k);
                            NodeList texts = ie.getElementsByTagNameNS(DMN_NS, "text");
                            if (texts.getLength() > 0) val = texts.item(0).getTextContent().trim();
                        }
                        row.add(val);
                    }
                    // outputs
                    NodeList outEntries = rule.getElementsByTagNameNS(DMN_NS, "outputEntry");
                    for (int k = 0; k < outputNames.size(); k++) {
                        String val = "";
                        if (k < outEntries.getLength()) {
                            Element oe = (Element) outEntries.item(k);
                            NodeList texts = oe.getElementsByTagNameNS(DMN_NS, "text");
                            if (texts.getLength() > 0) val = texts.item(0).getTextContent().trim();
                        }
                        row.add(val);
                    }

                    pw.println(joinCsv(row));
                }
            }

            System.out.println("Wrote: " + csv.getAbsolutePath());
        }
    }

    /** Joins and escapes a list of values as a CSV row. */
    private static String joinCsv(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(cells.get(i)));
        }
        return sb.toString();
    }

    /** Basic CSV-escaping: double any quotes and wrap in quotes if needed. */
    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needs = s.contains(",") || s.contains("\"") || s.contains("\n");
        String out = s.replace("\"", "\"\"");
        return needs ? "\"" + out + "\"" : out;
    }
}

