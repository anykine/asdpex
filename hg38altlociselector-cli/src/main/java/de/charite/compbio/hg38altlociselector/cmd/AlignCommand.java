/**
 * 
 */
package de.charite.compbio.hg38altlociselector.cmd;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.commons.cli.ParseException;

import com.google.common.collect.ImmutableList;

import de.charite.compbio.hg38altlociselector.Hg38altLociSeletorOptions;
import de.charite.compbio.hg38altlociselector.data.AccessionInfo;
import de.charite.compbio.hg38altlociselector.data.AltScaffoldPlacementInfo;
import de.charite.compbio.hg38altlociselector.data.AlternativeLociBuilder;
import de.charite.compbio.hg38altlociselector.data.AlternativeLocus;
import de.charite.compbio.hg38altlociselector.data.NCBIgffAlignment;
import de.charite.compbio.hg38altlociselector.data.NCBIgffAlignmentElement;
import de.charite.compbio.hg38altlociselector.data.NCBIgffAlignmentElementType;
import de.charite.compbio.hg38altlociselector.exceptions.AltLociSelectorException;
import de.charite.compbio.hg38altlociselector.exceptions.CommandLineParsingException;
import de.charite.compbio.hg38altlociselector.exceptions.HelpRequestedException;
import de.charite.compbio.hg38altlociselector.io.parser.NCBIgffAlignmentParser;
import de.charite.compbio.hg38altlociselector.util.IOUtil;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.SequenceUtil;

/**
 * 
 *
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class AlignCommand extends AltLociSelectorCommand {

	/**
	 * @param args
	 * @throws HelpRequestedException
	 * @throws CommandLineParsingException
	 */
	public AlignCommand(String[] args) throws CommandLineParsingException, HelpRequestedException {
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
			return new AlignCommandLineParser().parse(args);
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
		System.out.println("[INFO] Creating seed files");
		if (options == null)
			System.err.println("[ERROR] option = null");
		// alt. loci info parsing
		ImmutableList<AlternativeLocus> loci = new AlternativeLociBuilder(options.altAccessionsPath,
				options.altScaffoldPlacementPath, options.genomicRegionsDefinitionsPath).build();
		// Ref fasta File
		final ReferenceSequenceFile refFile = ReferenceSequenceFileFactory
				.getReferenceSequenceFile(new File(options.referencePath));
		if (!refFile.isIndexed()) {
			System.err.println("The FastA is not index - please index file first and run again.");
			System.exit(1);
		}
		// visualisation
		System.out.println("[INFO] processing alt. loci");
		System.out.println("0%       50%       100%");
		System.out.println("|.........|.........|");
		int c = 1;
		int limit = 0;
		for (AlternativeLocus locus : loci) {
			if (100.0 * c++ / loci.size() > limit) {
				limit += 5;
				System.out.print("*");
			}
			// identifier used for fastA and seed file
			String identifier = createFastaIdentifier(locus.getAccessionInfo());

			if (!identifier.equals("chr11_KI270831v1_alt"))
				continue;

			// identifier for the GFF file
			String filenameGFF = createGffIdentifier(locus.getPlacementInfo());

			System.out.println("\nIdentifier: " + identifier);
			System.out.println("GFF filename: " + filenameGFF);

			ImmutableList<NCBIgffAlignment> alignments = null;
			if (new File(options.alignmentPath, filenameGFF).exists()) {
				alignments = new NCBIgffAlignmentParser(new File(options.alignmentPath, filenameGFF)).parse();
			} else {
				System.err.println("File is missing: " + filenameGFF);
				continue;
			}

			int block = 1;
			for (NCBIgffAlignment alignment : alignments) {
				System.out.println("- process gff alignment: " + block + " | " + alignments.size());

				ArrayList<NCBIgffAlignment> indelSplitAlignments = splitupAlignmentAtLargeIndels(alignment);
				// System.out.println(alignment.getAltId() + ":\t" + alignment.getAltStart() + "\t"
				// + alignment.getAltStop() + "\t" + alignment.isAltStrand());
				// System.out.println(alignment.getRefId() + ":\t" + alignment.getRefStart() + "\t"
				// + alignment.getRefStop() + "\t" + alignment.isRefStrand());
				int splitC = 1;
				for (NCBIgffAlignment indelSplitAlignment : indelSplitAlignments) {
					System.out
							.println("- process Indel split alignment " + splitC + " | " + indelSplitAlignments.size());
					splitC++;
					// ALT LOCI
					byte[] altLoci = extractSequence(refFile, identifier, indelSplitAlignment.getAltStart(),
							indelSplitAlignment.getAltStop(), indelSplitAlignment.isAltStrand());
					byte[] ref = extractSequence(refFile, "chr" + locus.getPlacementInfo().getParentName(),
							indelSplitAlignment.getRefStart(), indelSplitAlignment.getRefStop(),
							indelSplitAlignment.isRefStrand());

					ArrayList<Tuple> list = getNblocks(altLoci);
					System.out.println("\tfound 'N' blocks: " + list.size());
					for (Tuple tuple : list) {
						if (tuple.end - tuple.start > 10) {
							System.out.println("\t'N' block (> 10bp) from: " + tuple.start + " - " + tuple.end);
						} else {
							System.out.println("\t'N' block (< 10bp) from: " + tuple.start + " - " + tuple.end);
							writeFilesToDisc(identifier, block, altLoci, ref);
						}
					}

					// splitupAlignmentAtNstrech(alignment, list);

					// if (block > 0)
					// continue;

					// // FASTA FILES
					// // alt loci
					// try {
					// createFastaFile(options.tempFolder + "/" + identifier + "_altLoci_" + block + ".fa", identifier,
					// altLoci, false);
					// } catch (IOException e) {
					// e.printStackTrace();
					// }
					// // ref
					// try {
					// createFastaFile(options.tempFolder + "/" + identifier + "_ref_" + block + ".fa", identifier,
					// ref, false);
					// } catch (IOException e) {
					// e.printStackTrace();
					// }
					//
					// // SEED FILES
					// try {
					// createMatchesFile(options.tempFolder, identifier + "_" + block + ".tab",
					// alignment.getElements(), 0, 0);
					// } catch (IOException e) {
					// System.err.println("[ERROR] failed to create seed info file for sample: "
					// + locus.getPlacementInfo().getAltScafAcc());
					// e.printStackTrace();
					// }

					// for (Tuple tuple : list) {
					// // System.out.println(tuple.start + " - " + tuple.end + " : "
					// // + new String(altLoci).substring(tuple.start, tuple.end));
					// System.out.println(tuple.start + " - " + tuple.end);
					// }
					block++;
				}
			}

			// int i = 0;
			// int counter = 0;
			// for (byte b : altLoci) {
			// if (b == 'N')
			// counter++;
			// }
			// System.out.println(identifier + ": " + counter);

			// write fasta files
			// try {
			// createFastaFile(options.tempFolder + "/" + identifier + "_altLoci.fa", identifier, altLoci, false);
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
			//
			// try {
			// createFastaFile(options.tempFolder + "/" + identifier + "_ref.fa", identifier, ref, false);
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
		}
		System.out.println("*");
	}

	private boolean writeFilesToDisc(String identifier, int block, byte[] altLoci, byte[] ref) {
		// FASTA FILES
		// alt loci
		try {
			createFastaFile(options.tempFolder + "/" + identifier + "_altLoci_" + block + ".fa", identifier, altLoci,
					false);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		// ref
		try {
			createFastaFile(options.tempFolder + "/" + identifier + "_ref_" + block + ".fa", identifier, ref, false);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		// // SEED FILES
		// try {
		// createMatchesFile(options.tempFolder, identifier + "_" + block + ".tab", alignment.getElements(), 0, 0);
		// } catch (IOException e) {
		// System.err.println(
		// "[ERROR] failed to create seed info file for sample: " + locus.getPlacementInfo().getAltScafAcc());
		// e.printStackTrace();
		// }
		return true;
	}

	/**
	 * Split the alignment into blocks at 'N'-Stretches.
	 * 
	 * @param alignment
	 * @param tuples
	 *            sorted list with non-overlapping
	 * @return
	 */
	private ArrayList<NCBIgffAlignment> splitupAlignmentAtNstrech(NCBIgffAlignment alignment, ArrayList<Tuple> tuples) {
		ArrayList<NCBIgffAlignment> alignments = new ArrayList<>();
		if (tuples.size() == 1) {
			alignments.add(alignment);
			return alignments;
		}

		int elemIdx = 0;
		int refLength = 0;
		int altLength = 0;
		int tupleIdx = 0;
		for (NCBIgffAlignmentElement element : alignment.getElements()) {
			switch (element.getType()) {
			case MATCH:
				if (altLength + element.getLength() >= tuples.get(tupleIdx).end) {
					System.err.println("Split inside Match");
					tupleIdx++;
				}
				refLength += element.getLength();
				altLength += element.getLength();
				break;
			case INSERTION:
				if (altLength + element.getLength() >= tuples.get(tupleIdx).end) {
					System.err.println("Split inside Insertion");
					tupleIdx++;
				}
				altLength += element.getLength();
				break;
			case DELETION:
				refLength += element.getLength();
				break;

			default:
				System.out.println("Was denn hier los?!");
				break;
			}
			if (tupleIdx >= tuples.size())
				break;
		}
		return alignments;
	}

	/**
	 * SPlit up the alignment at these curious large Insert/Deletions.
	 * 
	 * @param alignment
	 * @return
	 */
	private ArrayList<NCBIgffAlignment> splitupAlignmentAtLargeIndels(NCBIgffAlignment alignment) {
		ArrayList<NCBIgffAlignment> alignments = new ArrayList<>();
		final int LIMIT = 5000;
		int start = 0;
		int stop = start;
		int obacht = 0;
		// tmp
		// int ref = alignment.getRefStart() - 1;
		// int alt = alignment.getAltStart() - 1;
		for (NCBIgffAlignmentElement element : alignment.getElements()) {
			if (element.getType() == NCBIgffAlignmentElementType.INSERTION && element.getLength() >= LIMIT)
				obacht++;
			else if (element.getType() == NCBIgffAlignmentElementType.DELETION && element.getLength() >= LIMIT)
				obacht++;
			else
				obacht = 0;

			if (obacht > 1) {
				// NCBIgffAlignment subAlignment = extractSubAlignment(alignment, start, stop - 1);
				// System.out.println(subAlignment + "\n\n");
				// alignments.add(subAlignment);
				alignments.add(extractSubAlignment(alignment, start, stop - 1));
				start = stop + 1;
			}
			stop++;
		}
		// System.out.println("stop: " + stop + " elements: " + alignment.getElements().size());
		// NCBIgffAlignment subAlignment = extractSubAlignment(alignment, start, stop);
		// System.out.println(subAlignment + "\n\n");
		// alignments.add(subAlignment);
		if (start < stop)
			alignments.add(extractSubAlignment(alignment, start, stop));
		else
			alignments.add(alignment);

		// System.out.println("refend: " + ref + "\taltend: " + alt);
		// System.out.println("original:\n" + alignment + "\n\n");
		return alignments;
	}

	/**
	 * Extracts the subalignment from the {@link NCBIgffAlignment} object and returns a new {@link NCBIgffAlignment}
	 * object.
	 * 
	 * @param alignment
	 *            parent {@link NCBIgffAlignment} object from where the subalignment should be extracted
	 * @param startElem
	 *            the first {@link NCBIgffAlignmentElement} contained in the subalignment (0-based)
	 * @param endElem
	 *            the last {@link NCBIgffAlignmentElement} contained in the subalignment (excl.)
	 * @return a new {@link NCBIgffAlignment} object containing the {@link NCBIgffAlignmentElement}s defined by
	 *         startElem and endElem and updated coordinates for reference and alternative.
	 */
	private NCBIgffAlignment extractSubAlignment(NCBIgffAlignment alignment, int startElem, int endElem) {

		int refStart = alignment.getRefStart();
		int refStop = alignment.getRefStart() - 1; // 1-based --> remove 1 from cumulative length
		int altStart = alignment.getAltStart();
		int altStop = alignment.getAltStart() - 1; // 1-based --> remove 1 from cumulative length

		NCBIgffAlignmentElement element;
		for (int i = 0; i < endElem; i++) {
			element = alignment.getElements().get(i);
			if (i < startElem) {
				switch (element.getType()) {
				case INSERTION:
					altStart += element.getLength();
					break;
				case DELETION:
					refStart += element.getLength();
					break;
				case MATCH:
					refStart += element.getLength();
					altStart += element.getLength();
					break;

				default:
					break;
				}
			}

			switch (element.getType()) {
			case INSERTION:
				altStop += element.getLength();
				break;
			case DELETION:
				refStop += element.getLength();
				break;
			case MATCH:
				refStop += element.getLength();
				altStop += element.getLength();
				break;

			default:
				break;
			}

		}
		System.out.println(
				"\textract elements from: " + startElem + " - " + endElem + " von " + alignment.getElements().size());
		return new NCBIgffAlignment(alignment.getRefId(), alignment.getAltId(), refStart, refStop,
				alignment.isRefStrand(), altStart, altStop, alignment.isAltStrand(),
				alignment.getElements().subList(startElem, endElem));
	}

	/**
	 * Extract list
	 * 
	 * @param seq
	 * @return
	 */
	private ArrayList<Tuple> getNonNblocks(byte[] seq) {
		ArrayList<Tuple> list = new ArrayList<>();
		int start = 0;
		int stop = start;
		for (byte b : seq) {
			if (b == 'N') {
				if (start != stop)
					list.add(new Tuple(start, stop));
				start = stop + 1;
			}
			stop++;
		}
		list.add(new Tuple(start, stop));
		return list;
	}

	/**
	 * Extract list of {@link Tuple}s with 'N' positions.<br>
	 * The positions are '0'-based and the start is incl., the stop excl.
	 * 
	 * @param seq
	 *            the input sequence with alphabet [ACGTN]
	 * @return List of {@link Tuple}s with 'N'-blocks
	 */
	private ArrayList<Tuple> getNblocks(byte[] seq) {
		ArrayList<Tuple> list = new ArrayList<>();
		int start = 0;
		int stop = start;
		for (byte b : seq) {

			if (b != 'N') {
				if (start < stop) {
					list.add(new Tuple(start, stop));
				}
				start = stop + 1;
			}
			stop++;
		}
		if (start < stop) {
			list.add(new Tuple(start, stop));
		}
		return list;

	}

	/**
	 * Extract the Sequence from the
	 * 
	 * @param refFile
	 * @param id
	 * @param start
	 * @param stop
	 * @param strand
	 * @return
	 */
	private byte[] extractSequence(ReferenceSequenceFile refFile, String id, int start, int stop, boolean strand) {
		byte[] bases = refFile.getSubsequenceAt(id, start, stop).getBases();

		if (!strand)
			SequenceUtil.reverseComplement(bases);

		return bases;
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

	private String createGffIdentifier(AltScaffoldPlacementInfo info) {
		StringBuilder identifier = new StringBuilder();
		identifier.append(info.getAltScafAcc()).append("_").append(info.getParentAcc()).append(".gff");
		return identifier.toString();
	}

	/**
	 * 
	 * @param path
	 * @param name
	 * @param bases
	 * @param multiFasta
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private void createFastaFile(String path, String name, byte[] bases, boolean multiFasta)
			throws UnsupportedEncodingException, IOException {
		File file = new File(path);

		final BufferedWriter out;
		if (file.exists()) {
			if (options.singleAltLociFile) {
				System.out.println("[INFO] file already exists. Skipping.");
				return;
			}
		}
		file.getParentFile().mkdirs();
		if (multiFasta)
			out = IOUtil.getBufferedFileWriter(file, true);
		else
			out = IOUtil.getBufferedFileWriter(file);
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

	private void createMatchesFile(String path, String filename, ImmutableList<NCBIgffAlignmentElement> matches,
			int offset, int tail) throws IOException {

		File file = new File(path, filename);
		final BufferedWriter out;
		if (file.exists()) {
			System.out.println("[WARN] file already exists: " + file.getCanonicalPath());
		}
		file.getParentFile().mkdirs();
		out = IOUtil.getBufferedFileWriter(file);
		boolean first = true;
		int c = 0;

		for (NCBIgffAlignmentElement match : matches) {
			if (match.getType() != NCBIgffAlignmentElementType.MATCH)
				continue;
			c++;
			// extend the first seed to the begin of the region
			if (first) {
				// System.out.println(String.format("%d\t%d\t%d\n", 0, 0, match.getLength() + offset));
				out.write(String.format("%d\t%d\t%d\n", 0, 0, match.getLength() + offset));
				first = false;
				continue;
			}
			// System.out.println(c + "\t" + matches.size());
			if (c == matches.size()) {
				out.write(String.format("%d\t%d\t%d\n", match.getRef_start() + offset, match.getAlt_start() + offset,
						match.getLength() + tail));
				// System.out.println(String.format("%d\t%d\t%d\n", match.getRef_start() + offset,
				// match.getAlt_start() + offset, match.getLength() + tail));
				continue;
			}
			out.write(String.format("%d\t%d\t%d\n", match.getRef_start() + offset, match.getAlt_start() + offset,
					match.getLength()));
		}
		IOUtil.close(out);
	}

	/**
	 * Inner private class, which only contains a tuple of integers to store the start and stop of sequence blocks.
	 * 
	 *
	 * @author Marten Jäger <marten.jaeger@charite.de>
	 *
	 */
	private class Tuple {
		public Tuple(int start, int end) {
			this.start = start;
			this.end = end;
		}

		int start;
		int end;
	}

}
