/**
 * 
 */
package de.charite.compbio.asdpex.io.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import com.google.common.collect.ImmutableMap;

import de.charite.compbio.asdpex.data.AccessionInfo;
import de.charite.compbio.asdpex.data.AccessionInfo.AccessionInfoBuilder;
import de.charite.compbio.asdpex.exceptions.AccessionInfoParseException;
import de.charite.compbio.asdpex.util.IOUtil;

/**
 * Parser for the NCBI alts/chr_accession_GRCh..
 *
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class AccessionInfoParser {

    private File file;
    /** the logger object to use */
    // private static final Logger LOGGER = LoggerFactory.getLogger(AccessionInfoParser.class);

    /**
     * Number of tab-separated fields in then NCBI alts_accessions_xxx file
     */
    private static final int NFIELDS = 5;

    /**
     * Dummy to prevent from using
     */
    @SuppressWarnings("unused")
    private AccessionInfoParser() {
    }

    /**
     * Constructor
     * 
     * @param filepath
     *            path to the file with the accession infos
     */
    public AccessionInfoParser(String filepath) {
        this.file = new File(filepath);
    }

    /**
     * Processes the file and return a Map with the {@link AccessionInfo}s, where the key is the "RefSeq
     * Accession.version"
     * 
     * @return Map with (RefSeq Accession.version | AccessionInfo) pairs
     */
    public ImmutableMap<String, AccessionInfo> parse() {
        ImmutableMap.Builder<String, AccessionInfo> result = new ImmutableMap.Builder<String, AccessionInfo>();
        BufferedReader reader = null;
        // reader = this.open();
        String line;
        try {
            reader = IOUtil.getBufferedReaderFromFileName(this.file);
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.startsWith("#"))
                        continue;
                    AccessionInfoBuilder aiBuilder = createBuilderFromLine(line);
                    AccessionInfo info = aiBuilder.build();
                    result.put(info.getRefseqAccessionVersion(), info);
                } catch (AccessionInfoParseException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // LOGGER.error("failed to read line from file: " + this.file.getAbsolutePath());
            e.printStackTrace();
        }
        IOUtil.close(reader);
        return result.build();
    }

    private AccessionInfoBuilder createBuilderFromLine(String line) throws AccessionInfoParseException {
        AccessionInfoBuilder builder = new AccessionInfoBuilder();
        String[] fields = line.split("\t");
        if (fields.length != AccessionInfoParser.NFIELDS) {
            String error = String.format(
                    "Malformed line in NCBI alts_accessions_xxx file:\n%s\nExpected %d fields but there were %d", line,
                    NFIELDS, fields.length);
            throw new AccessionInfoParseException(error);
        }
        builder.chromosome(fields[0]);
        builder.refseqAccessionVersion(fields[1]);
        try {
            builder.refseqGi(Integer.parseInt(fields[2]));
        } catch (NumberFormatException e) {
            throw new AccessionInfoParseException("Failed to parse Integer from Regseq gi field entry: " + fields[2]);
        }
        builder.genbankAccessionVersion(fields[3]);
        try {
            builder.genbankGi(Integer.parseInt(fields[4]));
        } catch (NumberFormatException e) {
            throw new AccessionInfoParseException("Failed to parse Integer from GenBank gi field entry: " + fields[4]);
        }

        return builder;
    }

}
