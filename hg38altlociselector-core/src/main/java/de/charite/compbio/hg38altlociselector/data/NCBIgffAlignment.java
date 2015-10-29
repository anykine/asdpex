/**
 * 
 */
package de.charite.compbio.hg38altlociselector.data;

import com.google.common.collect.ImmutableList;

/**
 * slim Representation of the NCBI alignment.
 * 
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class NCBIgffAlignment {
	private final String refId;
	private final String altId;
	private final int refStart;
	private final int refStop;
	private final boolean refStrand;
	private final int altStart;
	private final int altStop;
	private final boolean altStrand;
	private final ImmutableList<NCBIgffAlignmentElement> elements;

	public NCBIgffAlignment(String refId, String altId, int refStart, int refStop, boolean refStrand, int altStart,
			int altStop, boolean altStrand, ImmutableList<NCBIgffAlignmentElement> elements) {
		this.refId = refId;
		this.altId = altId;
		this.refStart = refStart;
		this.refStop = refStop;
		this.refStrand = refStrand;
		this.altStart = altStart;
		this.altStop = altStop;
		this.altStrand = altStrand;
		this.elements = elements;
	}

	public String getRefId() {
		return refId;
	}

	public String getAltId() {
		return altId;
	}

	public int getRefStart() {
		return refStart;
	}

	public int getRefStop() {
		return refStop;
	}

	public boolean isRefStrand() {
		return refStrand;
	}

	public int getAltStart() {
		return altStart;
	}

	public int getAltStop() {
		return altStop;
	}

	public boolean isAltStrand() {
		return altStrand;
	}

	public ImmutableList<NCBIgffAlignmentElement> getElements() {
		return elements;
	}

	/**
	 * 
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getRefId()).append("\tRefSeq\tmacth\t").append(getRefStart()).append("\t").append(getRefStop())
				.append("\t0\t").append(isRefStrand()).append("\t.\tTarget=").append(getAltId()).append("\t")
				.append(getAltStart()).append("\t").append(getAltStop()).append("\t").append(isAltStrand())
				.append(";Gap=");
		for (NCBIgffAlignmentElement element : elements) {
			switch (element.getType()) {
			case MATCH:
				sb.append("M");
				break;
			case INSERTION:
				sb.append("I");
				break;
			case DELETION:
				sb.append("D");
				break;
			default:
				break;
			}
			sb.append(element.getLength()).append(" ");
		}
		return sb.toString();
	}

}
