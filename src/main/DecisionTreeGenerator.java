package main;

import java.nio.file.Path;
import java.util.Map;

import data.Dataset;
import data.Record;
import data.attribute.AbstractAttribute;
import data.attribute.Attributelist;
import data.value.AbstractValue;
import tree.DecisionTree;
import tree.edge.Branch;
import tree.node.InternalNode;
import tree.node.LeafNode;
import tree.node.Node;

public class DecisionTreeGenerator {
	public static double sizeThreshold = 0.1;

	public DecisionTreeGenerator() {
	}

	public DecisionTree run(Path datasetPath, Path attrlistPath) {
		Attributelist attrlist = new Attributelist(attrlistPath);
		Dataset trainData = new Dataset(datasetPath, attrlist);
		return run(trainData);
	}
	public DecisionTree run(Dataset trainData) {
		double baseSize = trainData.size();

		return new DecisionTree(generateDecisionTree(trainData, baseSize));
	}

	private Node generateDecisionTree(Dataset trainData, double baseSize) {
		Attributelist attrlist = trainData.getAttrlist();
		// 0.全TupleとAttributeListの次元の一致を確認
		if (!isReady(trainData)) {
			System.err.println("Input Error.");
			return null;
		}

		// 1.ルートNode作成
		Node node;

		// 2.全てのTupleのクラス属性値が同じCならルートにCをラベル付けして終了
		AbstractValue<?> commonClassValue = trainData.getCommonClassValue();
		if (commonClassValue != null)
			return new LeafNode(commonClassValue);

		// 3.Attributelistが空ならノードに全Tuple中最も多い属性値をラベルづけして終了
		if (attrlist.isEmpty())
			return new LeafNode(trainData.getMajorityClassValue());

		// x1.枝刈り。現データセットのサイズが元のデータセットの閾値s倍未満の場合，最多属性値をラベルづけして終了
		if (trainData.size() < sizeThreshold * baseSize)
			return new LeafNode(trainData.getMajorityClassValue());

		// 4.利得率から判定する属性を選び，分岐させる
		AbstractAttribute<?> bestAttr = trainData.getBestAttrByGainRation();
		if (bestAttr == null)
			return new LeafNode(trainData.getMajorityClassValue());
		node = new InternalNode(bestAttr);
		Map<AbstractValue<?>, Dataset> subDatasets = trainData.splitByAttr(bestAttr);
		for (Map.Entry<AbstractValue<?>, Dataset> valDataEntry : subDatasets.entrySet()) {
			AbstractValue<?> branchVal = valDataEntry.getKey();
			Branch branch = new Branch(branchVal);
			Dataset subDS = valDataEntry.getValue();

			if (subDS.isEmpty()) {
				AbstractValue<?> freqVal = trainData.getMajorityClassValue();
				LeafNode freqChild = new LeafNode(freqVal);
				node.addChildNode(branch, freqChild);
			} else {
				// 再帰的にノードを生成し繋げていく
				Node recurChild = generateDecisionTree(subDS, baseSize);
				node.addChildNode(branch, recurChild);
			}
		}
		return node;
	}

	private boolean isReady(Dataset trainData) {
		int dimension = trainData.getAttrlist().size();
		boolean b = true;
		for (Record r : trainData.getSet())
			if (r.size() != dimension+1){	// 次元の不一致
				System.err.println("ErrorRecord: " + r);
				b = false;
			}
		return b;
	}
}