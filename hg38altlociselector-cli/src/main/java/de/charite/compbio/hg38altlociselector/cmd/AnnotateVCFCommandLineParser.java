/**
 * 
 */
package de.charite.compbio.hg38altlociselector.cmd;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import de.charite.compbio.hg38altlociselector.Hg38altLociSeletorOptions;
import de.charite.compbio.hg38altlociselector.exceptions.HelpRequestedException;
import de.charite.compbio.hg38altlociselector.util.HelpFormatter;

/**
 * 
 *
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class AnnotateVCFCommandLineParser {
    /** options representation for the Apache commons command line parser */
    protected Options options;
    /** the Apache commons command line parser */
    protected CommandLineParser parser;

    public AnnotateVCFCommandLineParser() {
        initializeParser();
    }

    private void initializeParser() {
        options = new Options();
        options.addOption("h", "help", false, "show this help");
        options.addOption("v", "vcf", true, "VCF file to be annotated");
        options.addOption("a", "alt", true, "VCF file with alt loci information");
        options.addOption("o", "out", true, "VCF output file with the annotations");
        parser = new DefaultParser();
    }

    public Hg38altLociSeletorOptions parse(String[] args) throws ParseException, HelpRequestedException {
        CommandLine cmd = parser.parse(options, args);

        // Fill the resulting Options.
        Hg38altLociSeletorOptions result = new Hg38altLociSeletorOptions();
        result.command = Hg38altLociSeletorOptions.Command.ANNOTATE_VCF;

        if (cmd.hasOption("help")) {
            printHelp(result);
            throw new HelpRequestedException();
        }

        if (cmd.hasOption("vcf"))
            result.inputVcf = cmd.getOptionValue("vcf");
        else {
            result.error = "Missing sample VCF file: -v";
            printHelp(result);
        }

        if (cmd.hasOption("alt"))
            result.altlociVcf = cmd.getOptionValue("alt");
        else {
            // result.error = "Missing alt loci VCF file: -a";
            // printHelp(result);
        }

        return result;
    }

    private void printHelp(Hg38altLociSeletorOptions options) {
        StringBuilder sb = new StringBuilder();
        org.apache.commons.cli.HelpFormatter formatter = new org.apache.commons.cli.HelpFormatter();
        formatter.printHelp("java -jar hg38altlociselector.jar " + options.command.toString(), "options:", this.options,
                options.error, true);
        // HelpFormatter.printUsage(options.command.toString(), "wie das alles
        // funzt", options);
        System.exit(HelpFormatter.Failure.MISSING_VCF.ordinal());
    }
}
