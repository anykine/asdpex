/**
 * 
 */
package de.charite.compbio.asdpex.cmd;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.ArrayUtils;

import com.google.common.collect.ImmutableMap;

import de.charite.compbio.asdpex.Hg38altLociSeletorOptions;
import de.charite.compbio.asdpex.data.AccessionInfo;
import de.charite.compbio.asdpex.data.AltScaffoldPlacementInfo;
import de.charite.compbio.asdpex.data.RegionInfo;
import de.charite.compbio.asdpex.exceptions.AltLociSelectorException;
import de.charite.compbio.asdpex.exceptions.CommandLineParsingException;
import de.charite.compbio.asdpex.exceptions.HelpRequestedException;
import de.charite.compbio.asdpex.io.parser.AccessionInfoParser;
import de.charite.compbio.asdpex.io.parser.AltScaffoldPlacementParser;
import de.charite.compbio.asdpex.io.parser.RegionInfoParser;
import de.charite.compbio.asdpex.io.writer.FastaFileWriter;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.SequenceUtil;

/**
 * This command will create the fasta files later on used for the alignments.
 *
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class CreateFastaCommand extends AltLociSelectorCommand {

    /**
     * @param args
     * @throws HelpRequestedException
     * @throws CommandLineParsingException
     */
    public CreateFastaCommand(String[] args) throws CommandLineParsingException, HelpRequestedException {
        super(args);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.charite.compbio.hg38altlociselector.cmd.AltLociSelectorCommand# parseCommandLine(java.lang.String[])
     */
    @Override
    protected Hg38altLociSeletorOptions parseCommandLine(String[] args)
            throws CommandLineParsingException, HelpRequestedException {
        try {
            return new CreateFastaCommandLineParser().parse(args);
        } catch (ParseException e) {
            throw new CommandLineParsingException("Could not parse the command line.", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.charite.compbio.hg38altlociselector.cmd.AltLociSelectorCommand#run()
     */
    @Override
    public void run() throws AltLociSelectorException {
        System.out.println("[INFO] Creating fasta files");
        if (options == null)
            System.err.println("[ERROR] option = null");
        System.out.println("[INFO] Read alt_loci accessions");
        AccessionInfoParser aiParser = new AccessionInfoParser(options.getAltAccessionsPath());
        ImmutableMap<String, AccessionInfo> aiMap = aiParser.parse();
        System.out.println("[INFO] found " + aiMap.size() + " alt_loci");

        System.out.println("[INFO] Read alt_loci placement");
        AltScaffoldPlacementParser asParser = new AltScaffoldPlacementParser(options.getAltScaffoldPlacementPath());
        ImmutableMap<String, AltScaffoldPlacementInfo> asMap = asParser.parse();
        System.out.println("[INFO] found placement for " + asMap.size() + " alt_loci");

        System.out.println("[INFO] Read region definitions");
        RegionInfoParser regParser = new RegionInfoParser(options.getGenomicRegionsDefinitionsPath());
        ImmutableMap<String, RegionInfo> regMap = regParser.parse();
        System.out.println("[INFO] found " + regMap.size() + " regions definitions");

        final ReferenceSequenceFile refFile = ReferenceSequenceFileFactory
                .getReferenceSequenceFile(new File(options.getReferencePath()));
        System.out.println(refFile.isIndexed());

        for (AltScaffoldPlacementInfo scaffold : asMap.values()) {
            AccessionInfo currentAI = aiMap.get(scaffold.getAltScafAcc());
            RegionInfo currentReg = regMap.get(scaffold.getRegion());

            // TODO ALL OF THIS SHOULD BE PUT INTO A FASTA-FACTORY

            // sequence between region start and alt_loci start - to take from
            // reference
            int fiveprimeFillingStart = currentReg.getStart();
            int fiveprimeFillingStop = scaffold.getParentStart() - 1; // since
                                                                      // its
                                                                      // inclusive

            // sequence inserted from the alt loci - w/o the tails
            int altLociStart = scaffold.getAltScafStart();
            int altLociStop = scaffold.getAltScafStop();

            // sequence between alt_loci stop and region stop - to take from
            // reference
            int threeprimeFillingStart = scaffold.getParentStop() + 1; // since
                                                                       // its
                                                                       // inclusive
            int threeprimeFillingStop = currentReg.getStop();

            byte[] altExtended = new byte[0];
            // System.out.println(altExtended.length);

            // add 5' Tail
            if (fiveprimeFillingStart < fiveprimeFillingStop) {
                ReferenceSequence ref = refFile.getSubsequenceAt("chr" + scaffold.getParentName(),
                        fiveprimeFillingStart, fiveprimeFillingStop);
                altExtended = ArrayUtils.addAll(altExtended, ref.getBases());
            }
            // System.out.println(altExtended.length);

            String identifier = createFastaIdentifier(currentAI);
            // add alt_loci
            byte[] bases = refFile.getSubsequenceAt(identifier, altLociStart, altLociStop).getBases();

            if (!scaffold.isStrand())
                SequenceUtil.reverseComplement(bases);

            altExtended = ArrayUtils.addAll(altExtended, bases);
            // System.out.println(altExtended.length);

            // add 3' tail
            if (threeprimeFillingStart < threeprimeFillingStop) {
                ReferenceSequence ref = refFile.getSubsequenceAt("chr" + scaffold.getParentName(),
                        threeprimeFillingStart, threeprimeFillingStop);
                altExtended = ArrayUtils.addAll(altExtended, ref.getBases());
            }
            // System.out.println(altExtended.length + "\t" +
            // (currentReg.getStop() - currentReg.getStart() + 1));

            try {
                if (options.isSingleAltLociFile())
                    FastaFileWriter.createFastaFile(
                            new File(options.getFastaOutputPath() + "/altLoci_single", identifier + "_extended.fa"),
                            identifier, altExtended, false);
                else
                    FastaFileWriter.createFastaFile(new File(options.getFastaOutputPath() + "/altLoci",
                            currentReg.getRegionName() + "_altLoci.fa"), identifier, altExtended, true);

            } catch (IOException e) {
                e.printStackTrace();
            }

            // now we also have to create the corresponding regions references
            ReferenceSequence reg = refFile.getSubsequenceAt("chr" + scaffold.getParentName(), currentReg.getStart(),
                    currentReg.getStop());
            try {
                FastaFileWriter.createFastaFile(
                        new File(options.getFastaOutputPath() + "/regions", currentReg.getRegionName() + ".fa"),
                        currentReg.getRegionName(), reg.getBases(), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates the Fasta identifier from accessionInfo file row in the format they are used in the reference fasta
     * files: chr<1-22|X|Y|M>_<GenBank Accession.version with '.'->'v'>_alt<br>
     * e.g.: chr21_GL383580v2_alt
     * 
     * @param info
     * @return
     */
    private String createFastaIdentifier(AccessionInfo info) {
        StringBuilder identifier = new StringBuilder();
        identifier.append("chr").append(info.getChromosome()).append("_")
                .append(info.getGenbankAccessionVersion().replace('.', 'v')).append("_alt");
        return identifier.toString();
    }

}
