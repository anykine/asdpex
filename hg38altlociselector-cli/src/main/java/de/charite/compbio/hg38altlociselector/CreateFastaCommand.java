/**
 * 
 */
package de.charite.compbio.hg38altlociselector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.ArrayUtils;

import com.google.common.collect.ImmutableMap;

import de.charite.compbio.hg38altlociselector.cmd.AltLociSelectorCommand;
import de.charite.compbio.hg38altlociselector.cmd.CreateFastaCommandLineParser;
import de.charite.compbio.hg38altlociselector.data.AccessionInfo;
import de.charite.compbio.hg38altlociselector.data.AltScaffoldPlacementInfo;
import de.charite.compbio.hg38altlociselector.data.RegionInfo;
import de.charite.compbio.hg38altlociselector.exceptions.AltLociSelectorException;
import de.charite.compbio.hg38altlociselector.exceptions.CommandLineParsingException;
import de.charite.compbio.hg38altlociselector.exceptions.HelpRequestedException;
import de.charite.compbio.hg38altlociselector.io.parser.AccessionInfoParser;
import de.charite.compbio.hg38altlociselector.io.parser.AltScaffoldPlacementParser;
import de.charite.compbio.hg38altlociselector.io.parser.RegionInfoParser;
import de.charite.compbio.hg38altlociselector.util.IOUtil;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;

/**
 * 
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
	 * @see de.charite.compbio.hg38altlociselector.cmd.AltLociSelectorCommand#parseCommandLine(java.lang.String[])
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
		AccessionInfoParser aiParser = new AccessionInfoParser(options.altAccessionsPath);
		ImmutableMap<String, AccessionInfo> aiMap = aiParser.parse();
		System.out.println("[INFO] found " + aiMap.size() + " alt_loci");

		System.out.println("[INFO] Read alt_loci placement");
		AltScaffoldPlacementParser asParser = new AltScaffoldPlacementParser(options.altScaffoldPlacementPath);
		ImmutableMap<String, AltScaffoldPlacementInfo> asMap = asParser.parse();
		System.out.println("[INFO] found placement for " + asMap.size() + " alt_loci");

		System.out.println("[INFO] Read alt_loci placement");
		RegionInfoParser regParser = new RegionInfoParser(options.genomicRegionsDefinitionsPath);
		ImmutableMap<String, RegionInfo> regMap = regParser.parse();
		System.out.println("[INFO] found " + regMap.size() + " regions definitions");

		final ReferenceSequenceFile refFile = ReferenceSequenceFileFactory
				.getReferenceSequenceFile(new File(options.referencePath));
		System.out.println(refFile.isIndexed());

		// ReferenceSequence seq;
		// while ((seq = refFile.nextSequence()) != null) {
		// System.out.println(seq.getName() + "\t" + seq.getBases().length);
		// }

		for (AltScaffoldPlacementInfo scaffold : asMap.values()) {
			AccessionInfo currentAI = aiMap.get(scaffold.getAltScafAcc());
			RegionInfo currentReg = regMap.get(scaffold.getRegion());
			System.out.println();
			System.out.println(createFastaIdentifier(currentAI));
			// break;

			System.out.println(currentReg.getStart() + "\t" + currentReg.getStop());
			System.out.println(scaffold.getParentStart() + "\t" + scaffold.getParentStop());

			// ALT_LOCI
			// ReferenceSequence alt = refFile.getSequence(createFastaIdentifier(currentAI));
			// System.out.println(scaffold.getAltScafStop() + scaffold.getAltStopTail() + "\t" + alt.getBases().length);

			// TODO ALL OF THIS SHOULD BE PUT INTO A FASTA-FACTORY

			// sequence between region start and alt_loci start - to take from reference
			int fiveprimeFillingStart = currentReg.getStart();
			int fiveprimeFillingStop = scaffold.getParentStart() - 1; // since its inclusive

			// sequence inserted from the alt loci - w/o the tails
			int altLociStart = scaffold.getAltScafStart();
			int altLociStop = scaffold.getAltScafStop();

			// sequence between alt_loci stop and region stop - to take from reference
			int threeprimeFillingStart = scaffold.getParentStop() + 1; // since its inclusive
			int threeprimeFillingStop = currentReg.getStop();

			byte[] altExtended = new byte[0];
			System.out.println(altExtended.length);

			// add 5' Tail
			if (fiveprimeFillingStart < fiveprimeFillingStop) {
				ReferenceSequence ref = refFile.getSubsequenceAt("chr" + scaffold.getParentName(),
						fiveprimeFillingStart, fiveprimeFillingStop);
				altExtended = ArrayUtils.addAll(altExtended, ref.getBases());
			}
			System.out.println(altExtended.length);

			String identifier = createFastaIdentifier(currentAI);
			// add alt_loci
			ReferenceSequence alt = refFile.getSubsequenceAt(identifier, altLociStart, altLociStop);
			altExtended = ArrayUtils.addAll(altExtended, alt.getBases());
			System.out.println(altExtended.length);

			// add 3' tail
			if (threeprimeFillingStart < threeprimeFillingStop) {
				ReferenceSequence ref = refFile.getSubsequenceAt("chr" + scaffold.getParentName(),
						threeprimeFillingStart, threeprimeFillingStop);
				altExtended = ArrayUtils.addAll(altExtended, ref.getBases());
			}
			System.out.println(altExtended.length + "\t" + (currentReg.getStop() - currentReg.getStart() + 1));

			try {
				createFastaFile(options.fastqOutputPath + "/altLoci/" + identifier + "_extended.fa", identifier,
						altExtended);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// now we also have to create the corresponding regions references
			ReferenceSequence reg = refFile.getSubsequenceAt("chr" + scaffold.getParentName(), currentReg.getStart(),
					currentReg.getStop());
			try {
				createFastaFile(options.fastqOutputPath + "/regions/" + currentReg.getRegionName() + ".fa",
						currentReg.getRegionName(), reg.getBases());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private String createFastaIdentifier(AccessionInfo info) {
		StringBuilder identifier = new StringBuilder();
		identifier.append("chr").append(info.getChromosome()).append("_")
				.append(info.getGenbankAccessionVersion().replace('.', 'v')).append("_alt");
		return identifier.toString();
	}

	private void createFastaFile(String path, String name, byte[] bases)
			throws UnsupportedEncodingException, IOException {
		File file = new File(path);

		if (file.exists()) {
			System.out.println("[INFO] file already exists. Skipping.");
			return;
		}
		file.getParentFile().mkdirs();
		final BufferedWriter out = IOUtil.getBufferedFileWriter(file);
		out.write(">");
		out.write(name);
		out.write("\n");

		for (int i = 0; i < bases.length; ++i) {
			if (i > 0 && i % options.fastaLineLength == 0)
				out.write("\n");
			out.write(bases[i]);
		}

		out.write("\n");
		IOUtil.close(out);
	}

}
