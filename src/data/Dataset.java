package data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import data.attribute.AbstractAttribute;
import data.attribute.Attributelist;
import data.attribute.ContinuousAttribute;
import data.attribute.NominalAttribute;
import data.value.AbstractValue;
import data.value.ContinuousValue;
import data.value.NominalValue;

public class Dataset implements Cloneable{
	private Set<Record> records;
	private Attributelist attrlist;

	/** コンストラクタ */
	public Dataset(Set<Record> recordSet, Attributelist attrlist) {
		this.records = recordSet;
		this.attrlist = attrlist;
	}
	public Dataset(Attributelist attrlist) {
		this(new HashSet<Record>(), attrlist);
	}
	public Dataset(Path path, Attributelist attrlist) {
		try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
			this.records = stream
					.map(values -> new Record(values, attrlist))
					.collect(Collectors.toSet());
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.attrlist = attrlist;
		// Recordを全て用意したあと，属性リストから連続値属性を探し出して置き換える処理を呼ぶ
		replaceContinuousAttribute();
	}

	/* getter */
	public Set<Record> getRecordSet() {
		return records;
	}
	public Attributelist getAttrlist() {
		return attrlist;
	}
	/* Set用メソッド */
	public int size() {
		return records.size();
	}
	public boolean isEmpty() {
		return records.isEmpty();
	}
	public boolean add(Record rcd) {
		return records.add(rcd);
	}

	/** clone */
	@Override
	public Dataset clone() {
		try {
			Dataset c = (Dataset) super.clone();
	    	c.records = new HashSet<>(this.records);
	    	c.attrlist = this.attrlist.clone();
	    	return c;
	    } catch (CloneNotSupportedException ce) {
            ce.printStackTrace();
	    }
	    return null;
	}

	/** 各属性をみて値が連続値なら数値属性(ContinuousAttribute)に置き換える */
	public void replaceContinuousAttribute() {
		ListIterator<AbstractAttribute<?>> attrItr = attrlist.listIterator();
		while (attrItr.hasNext()) {
			AbstractAttribute<?> attr = attrItr.next();
			if (!(attr instanceof NominalAttribute))
				continue;
			NominalAttribute nomAttr = (NominalAttribute) attr;
			if(nomAttr.hasOnlyNumber())					// 全ての属性値が連続値なら
				attrItr.set(nomAttr.toContinuous());	// 数値属性に置き換える
		}
	}

	/**
	 * 全てのレコードが同じクラス属性値をもつならその属性値を、2種類以上のクラス属性値があればnullを返す。
	 * @return 全てのレコードの同じクラス属性値。不一致の場合null
	 */
	public NominalValue getCommonClassValue() {
		NominalValue nv = null;
		for (Record r : records) {
			if (nv!=null)
				if (!nv.equals(r.getClassValue()))
					return null;
			nv = r.getClassValue();
		}
		return nv;
	}

	/**
	 * 全てのタプルのクラス属性値でもっとも多いものを返す。
	 * @return 最頻度のクラス属性値
	 */
	public NominalValue getMajorityClassValue() {
		// カウントパート
		Map<NominalValue, Integer> valueFreqency = countClassFreqency();
		// 最頻度属性値選出パート
		NominalValue majValue = null;
		int highFreq = 0;
		for (Map.Entry<NominalValue, Integer> entry : valueFreqency.entrySet()) {
			int freq = entry.getValue();
			if (freq > highFreq) {
				majValue = entry.getKey();
				highFreq = freq;
			}
		}
		return majValue;
	}
	/**
	 * クラス属性値ごとのデータセットでの出現頻度をMapにして返す
	 * @return クラス属性値の出現頻度
	 */
	private Map<NominalValue, Integer> countClassFreqency() {
		Map<NominalValue, Integer> classValFreq = new HashMap<>();
		for (Record r : records) {
			NominalValue nv = r.getClassValue();
			int count = classValFreq.getOrDefault(nv, 0) + 1;
			classValFreq.put(nv, count);
		}
		return classValFreq;
	}

	/**
	 * 情報利得率で分割属性を決める。
	 * @param 情報利得の閾値
	 * @return 情報利得率が最高の属性。情報利得が閾値未満の場合はnull。
	 */
	public AbstractAttribute<?> bestAttrByGainRation(double gainRateThreshold) {
		double gainSum = 0;			// 情報利得の平均を取るために使う
		double maxGainRatio = 0;
		AbstractAttribute<?> bestAttr = null;
		for (AbstractAttribute<?> attr : attrlist.getList()) {
			gainSum += gain(attr);
			//System.out.print("\t"+attr+":\t");
			double gainRatio = gainRatio(attr);
			//System.out.println("\tGainRatio= " + gainRatio);
			if (gainRatio > maxGainRatio) {
				maxGainRatio = gainRatio;
				bestAttr = attr;
			}
		}
		System.out.println("bestAttr: " + bestAttr);	// TODO
		if(bestAttr == null)
			return bestAttr;

		double gainAve = gainSum / attrlist.size();
		// x2.枝刈り．最高利得率と平均利得率の比がgainRate未満か
		if (gain(bestAttr) < gainRateThreshold * gainAve) {
			bestAttr = null;
			//System.out.println("However, under gain average " + gainAve);
		}

		return bestAttr;
	}
	/** 情報量(エントロピー) */
	private double info() {
		double info = 0;
		double thisSize = size();
		Map<NominalValue, Integer> classValFreq = countClassFreqency();
		for (Map.Entry<NominalValue, Integer> valFreqEntry : classValFreq.entrySet()) {
			double freq = valFreqEntry.getValue();
			double prob = freq/thisSize;
			info -= prob * Math.log(prob)/Math.log(2);
		}
		return info;
	}
	/** 属性attrで分割した後の情報量 */
	private double infoByAttr(AbstractAttribute<?> attr) {
		double infoAttr = 0;
		double thisSize = size();
		Map<AbstractValue<?>, Dataset> subDatasets = splitByAttr(attr);
		for (Map.Entry<AbstractValue<?>, Dataset> valDataEntry : subDatasets.entrySet()) {
			Dataset subDS = valDataEntry.getValue();
			double subSize = subDS.size();
			infoAttr += subSize / thisSize * subDS.info();
		}
		return infoAttr;
	}
	/** 属性attrによる情報利得 */
	private double gain(AbstractAttribute<?> attr) {
		return info() - infoByAttr(attr);
	}
	/** 属性attrによる全情報量 */
	private double splitInfoByAttr(AbstractAttribute<?> attr) {
		double splitInfo = 0;
		double thisSize = size();
		Map<AbstractValue<?>, Dataset> subDatasets = splitByAttr(attr);
		for (Map.Entry<AbstractValue<?>, Dataset> valDataEntry : subDatasets.entrySet()) {
			double subSize = valDataEntry.getValue().size();
			double sizeRate = subSize / thisSize;
			splitInfo -= sizeRate * Math.log(sizeRate)/Math.log(2);
		}
		return splitInfo;
	}
	/** 情報利得率 */
	private double gainRatio(AbstractAttribute<?> attr) {
		double infoGain = gain(attr);
		//System.out.print(" InfoGain= " + infoGain);//TODO
		return infoGain / splitInfoByAttr(attr);
	}


	/**
	 * 指定された属性の値ごとにデータセットを分割する。
	 * @param splitAttr 分割基準の属性
	 * @return 指定属性の値と分割されたサブデータセットのマップ
	 */
	public Map<AbstractValue<?>, Dataset> splitByAttr(AbstractAttribute<?> splitAttr) {
		Map<AbstractValue<?>, Dataset> subsetsMap = new HashMap<>(splitAttr.getAllValues().size());
		if (splitAttr instanceof NominalAttribute) {
			NominalAttribute splitNA = (NominalAttribute) splitAttr;
			subsetsMap.putAll(splitByNominalAttr(splitNA));
		} else if (splitAttr instanceof ContinuousAttribute) {
			ContinuousAttribute splitCA = (ContinuousAttribute) splitAttr;
			subsetsMap.putAll(splitByContinuousAttr(splitCA));
		}
		// 各サブデータセットとその属性リストから指定属性を削除する
		for (Dataset ds : subsetsMap.values()) {
			ds.removeValueInAttr(splitAttr);
		}
		return subsetsMap;
	}
	/** 離散値属性を基準に分割 */
	private Map<NominalValue, Dataset> splitByNominalAttr(NominalAttribute splitNA) {
		// データセット中の指定属性の値を全種類集める
		Set<AbstractValue<?>> allValues = valuesInAttr(splitNA);
		Map<NominalValue, Dataset> subsetsMap = new HashMap<>(allValues.size());
		// 該当属性の値の分だけ空のサブデータセットを用意
		for (AbstractValue<?> nomVal : allValues)
			subsetsMap.put((NominalValue) nomVal, new Dataset(attrlist.clone()));

		// 各レコードをチェックしてサブデータセットに振り分ける
		for (Record rcd : records) {
			// key
			NominalValue nomVal = (NominalValue) rcd.getValueInAttr(splitNA);
			// value
			Dataset subDS = subsetsMap.get(nomVal);
			// レコードをサブデータセットに追加
			subDS.add(rcd.clone());
		}
		return subsetsMap;
	}
	/** 連続値属性を基準に分割 */
	private Map<ContinuousValue, Dataset> splitByContinuousAttr(ContinuousAttribute splitCA) {
		//Map<ContinuousValue, Dataset> subsetsMap = new HashMap<>(splitCA.getAllValues().size());
		// TODO
		return null;
	}

	private Set<AbstractValue<?>> valuesInAttr(AbstractAttribute<?> attr) {
		Set<AbstractValue<?>> allValues = new HashSet<>();
		for (Record r : records)
			allValues.add(r.getValueInAttr(attr));
		return allValues;
	}
	public Set<NominalValue> classValues() {
		Set<NominalValue> allValues = new HashSet<>();
		for (Record r : records)
			allValues.add(r.getClassValue());
		return allValues;
	}
	private void removeValueInAttr(AbstractAttribute<?> removeAttr) {
		attrlist.remove(removeAttr);
		for (Record r : records)
			r.removeValueInAttr(removeAttr);
	}
}