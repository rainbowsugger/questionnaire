package com.baoshine.questionnaire.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baoshine.common.exception.ResultCodeEnum;
import com.baoshine.questionnaire.constant.ExcludeFields;
import com.baoshine.questionnaire.entity.*;
import com.baoshine.questionnaire.exception.QuestionnaireException;
import com.baoshine.questionnaire.repository.BusinessNodeRepository;
import com.baoshine.questionnaire.repository.GenericRepository;
import com.baoshine.questionnaire.service.BusinessQuestionnaireService;
import com.baoshine.questionnaire.service.QuestionnaireService;
import com.baoshine.questionnaire.utils.ListBeanConvertUtil;
import com.baoshine.questionnaire.vo.BusinessAnswerOptionVO;
import com.baoshine.questionnaire.vo.BusinessNodeVO;
import com.baoshine.questionnaire.vo.BusinessQuestionnaireVO;
import com.baoshine.questionnaire.vo.PathVO;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BusinessQuestionnaireServiceImpl implements BusinessQuestionnaireService {

    private final GenericRepository genericRepository;

    private final QuestionnaireService questionnaireService;

    private final BusinessNodeRepository businessNodeRepository;

    @Autowired
    public BusinessQuestionnaireServiceImpl(GenericRepository genericRepository,
                                            QuestionnaireService questionnaireService,
                                            BusinessNodeRepository businessNodeRepository) {
        this.genericRepository = genericRepository;
        this.questionnaireService = questionnaireService;
        this.businessNodeRepository = businessNodeRepository;
    }

    /**
     * ????????????????????????
     *
     * @param businessQuestionnaireVO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessQuestionnaire saveBusinessQuestionnaire(BusinessQuestionnaireVO businessQuestionnaireVO) {
        BusinessQuestionnaire businessQuestionnaire =
                ListBeanConvertUtil.convert(businessQuestionnaireVO, BusinessQuestionnaire.class);
        return genericRepository.save(businessQuestionnaire);
    }

    /**
     * ??????????????????
     *
     * @param businessId
     * @param businessType
     * @return
     */
    @Override
    public List<BusinessQuestionnaire> queryAllQuestionnaire(Long businessId, Long businessType) {
        Specification<BusinessQuestionnaire> specification =
                (Specification<BusinessQuestionnaire>) (root, criteriaQuery, criteriaBuilder) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    Optional.ofNullable(businessId)
                            .ifPresent(item -> predicates.add(criteriaBuilder.equal(root.get("businessId"), item)));
                    Optional.ofNullable(businessType)
                            .ifPresent(item -> predicates.add(criteriaBuilder.equal(root.get("businessType"), item)));
                    return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
                };
        return genericRepository.findAllBy(BusinessQuestionnaire.class, specification);
    }

    /**
     * ???????????????????????????
     *
     * @param businessQuestionnaireId
     * @return
     */
    @Override
    public BusinessQuestionnaire displayAllQuestion(Long businessQuestionnaireId) throws QuestionnaireException {
        return genericRepository.findById(BusinessQuestionnaire.class, businessQuestionnaireId)
                .orElseThrow(() -> new QuestionnaireException(
                        ResultCodeEnum.CMN_QUERY_RESULT_NULL));
    }

    /**
     * ????????????????????????
     *
     * @param nodeId
     * @return
     */
    @Override
    public List<Node> displayNextQuestion(Long questionnaireId, Long nodeId) throws QuestionnaireException {
        return questionnaireService.nextQuestions(questionnaireId, nodeId);
    }

    /**
     * ??????????????????(???????????????????????????)
     *
     * @param businessNodeVO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Node> saveQuestionStep(BusinessNodeVO businessNodeVO) throws QuestionnaireException {
        boolean noOtherOption = false;
        int otherOptionCount = 0;
        List<Node> result = new ArrayList<>();
        //??????????????????????????????
        businessNodeRepository.save(convertToEntity(businessNodeVO));
        //??????????????????????????????
        List<BusinessNode> businessNodes = businessNodeRepository.findByBusinessQuestionnaire(businessNodeVO.getBusinessQuestionnaireVO().getId());
        Map<Long, BusinessNode> businessNodeMap = businessNodes.stream().collect(Collectors.toMap(
                BusinessNode::getNodeId, businessNode -> businessNode, (o, n) -> n));
        //??????????????????????????????
        Long questionnaireId = businessNodeVO.getBusinessQuestionnaireVO().getQuestionnaireId();
        Questionnaire questionnaire = genericRepository.findById(Questionnaire.class, questionnaireId)
                .orElseThrow(() -> new QuestionnaireException(ResultCodeEnum.CMN_QUERY_RESULT_NULL));
        List<Node> nodes = questionnaire.getNodeList();
        List<Path> paths = questionnaire.getPathList();
        Map<Long, Node> nodeMap =
                nodes.stream().collect(Collectors.toMap(UUIDEntity::getId, node -> node, (o, n) -> n));
        List<Path> childPaths = paths.stream().filter(path -> path.getParentNodeId().equals(businessNodeVO.getNodeId()))
                .collect(Collectors.toList());
        for (Path path : childPaths) {
            //???????????????
            Node childNode = nodeMap.get(path.getChildNodeId());
            //????????????????????????????????????????????????
            List<Path> parentPaths = paths.stream().filter(p -> p.getChildNodeId().equals(childNode.getId()) &&
                    !p.getParentNodeId().equals(businessNodeVO.getNodeId())).collect(
                    Collectors.toList());
            if (!CollectionUtils.isEmpty(parentPaths)) {
                for (Path parentPath : parentPaths) {
                    if (!CollectionUtils.isEmpty(path.getAnswerOptions()) &&
                            !checkAnswerOptions(parentPath.getAnswerOptions(),
                                    businessNodeMap.get(parentPath.getParentNodeId()).getOptionList())) {
                        otherOptionCount++;
                    }
                }
                if (otherOptionCount == 0) {
                    noOtherOption = true;
                }
            } else {
                noOtherOption = true;
            }
            //???????????????????????????????????????????????????????????????????????????????????????
            if ((CollectionUtils.isEmpty(path.getAnswerOptions()) || checkAnswerOptions(path.getAnswerOptions(),
                    businessNodeMap.get(businessNodeVO.getNodeId()).getOptionList()))
                    && noOtherOption) {
                result.add(childNode);
                result.addAll(displayNextQuestion(questionnaireId, childNode.getId()));
            }
        }
        return result;
    }

    @Override
    public BusinessQuestionnaireVO initialQuestionnaire(BusinessQuestionnaireVO businessQuestionnaireVO) throws QuestionnaireException {
        Questionnaire questionnaire =
                questionnaireService.loadQuestionnaire(businessQuestionnaireVO.getQuestionnaireId());
        BeanUtil.copyProperties(questionnaire, businessQuestionnaireVO,
                ExcludeFields.excludeField);
        BusinessQuestionnaire businessQuestionnaire = saveBusinessQuestionnaire(businessQuestionnaireVO);
        List<Node> nodes = new ArrayList<>();
        Node rootNode =
                questionnaire.getNodeList().stream().filter(Node::isRootNodeIndi).collect(Collectors.toList())
                        .get(0);
        nodes.add(rootNode);
        nodes.addAll(displayNextQuestion(businessQuestionnaire.getQuestionnaireId(), rootNode.getId()));
        List<BusinessNodeVO> businessNodeVOS = nodes.stream().map(this::convertToBusiness
        ).collect(Collectors.toList());
        BusinessQuestionnaireVO result = ListBeanConvertUtil.convert(businessQuestionnaire, BusinessQuestionnaireVO.class);
        businessNodeVOS.forEach(business -> business.setBusinessQuestionnaireVO(result));
        result.setBusinessNodeVOS(businessNodeVOS);
        return result;
    }


    private boolean checkAnswerOptions(List<AnswerOption> answerOptions,
                                       List<BusinessAnswerOption> businessAnswerOptions) {
        List<Long> condition =
                answerOptions.stream().map(answerOption -> answerOption.getId()).collect(Collectors.toList());
        List<Long> result = businessAnswerOptions.stream().filter(option -> option.isSelected() == true)
                .map(businessAnswerOption -> businessAnswerOption.getAnswerOptionId()).collect(
                        Collectors.toList());
        if (ListUtils.isEqualList(condition, result)) {
            return true;
        }
        return false;
    }

    public BusinessNode convertToEntity(BusinessNodeVO businessNodeVO) {
        BusinessNode businessNode = ListBeanConvertUtil.convert(businessNodeVO, BusinessNode.class);
        List<BusinessAnswerOption> options = businessNodeVO.getOptionVOS().stream().map(p -> {
            BusinessAnswerOption businessAnswerOption = ListBeanConvertUtil.convert(p, BusinessAnswerOption.class);
            businessAnswerOption.setBusinessNode(businessNode);
            return businessAnswerOption;
        }).collect(Collectors.toList());
        businessNode
                .getOptionList().addAll(options);
        businessNode.setBusinessQuestionnaire(
                ListBeanConvertUtil.convert(businessNodeVO.getBusinessQuestionnaireVO(), BusinessQuestionnaire.class));
        return businessNode;
    }

    public BusinessNodeVO convertToVO(BusinessNode businessNode) {
        BusinessNodeVO businessNodeVO = ListBeanConvertUtil.convert(businessNode, BusinessNodeVO.class);
        businessNodeVO
                .getOptionVOS().addAll(businessNode.getOptionList().stream().map(this::convertToVO).collect(Collectors.toList()));
        businessNodeVO.setBusinessQuestionnaireVO(
                ListBeanConvertUtil.convert(businessNode.getBusinessQuestionnaire(), BusinessQuestionnaireVO.class));
        return businessNodeVO;
    }

    public BusinessAnswerOption convertToEntity(BusinessAnswerOptionVO businessAnswerOptionVO) {
        BusinessAnswerOption businessAnswerOption = ListBeanConvertUtil.convert(businessAnswerOptionVO, BusinessAnswerOption.class);
        businessAnswerOption.setBusinessNode(ListBeanConvertUtil.convert(businessAnswerOptionVO.getBusinessNodeVO(), BusinessNode.class));
        return businessAnswerOption;
    }

    public BusinessAnswerOptionVO convertToVO(BusinessAnswerOption businessAnswerOption) {
        BusinessAnswerOptionVO businessAnswerOptionVO = ListBeanConvertUtil.convert(businessAnswerOption, BusinessAnswerOptionVO.class);
        businessAnswerOptionVO.setBusinessNodeVO(ListBeanConvertUtil.convert(businessAnswerOption.getBusinessNode(), BusinessNodeVO.class));
        return businessAnswerOptionVO;
    }



    public BusinessNodeVO convertToBusiness(Node node) {
        BusinessNodeVO businessNodeVO = new BusinessNodeVO();
        BeanUtils.copyProperties(node.getQuestion(), businessNodeVO, ExcludeFields.excludeField);
        BeanUtil.copyProperties(node, businessNodeVO, ExcludeFields.excludeField);
        businessNodeVO.setQuestionId(node.getQuestion().getId());
        List<BusinessAnswerOptionVO> optionVOS = node.getQuestion().getAnswerOptions().stream().map(option -> {
            BusinessAnswerOptionVO businessAnswerOptionVO =
                    ListBeanConvertUtil.convert(option, BusinessAnswerOptionVO.class, ExcludeFields.excludeField);
            businessAnswerOptionVO.setAnswerOptionId(option.getId());
            //businessAnswerOptionVO.setBusinessNodeVO(businessNodeVO);
            return businessAnswerOptionVO;
        }).collect(Collectors.toList());
        businessNodeVO.getOptionVOS().addAll(optionVOS);
        businessNodeVO.setNodeId(node.getId());
        return businessNodeVO;
    }

    public BusinessQuestionnaireVO convertToVO(BusinessQuestionnaire businessQuestionnaire)
            throws QuestionnaireException {
        BusinessQuestionnaireVO businessQuestionnaireVO =
                ListBeanConvertUtil.convert(businessQuestionnaire, BusinessQuestionnaireVO.class);
        Questionnaire questionnaire = questionnaireService.loadQuestionnaire(businessQuestionnaire.getQuestionnaireId());
        businessQuestionnaireVO.setPathVOS(ListBeanConvertUtil.convert(questionnaire.getPathList(), PathVO.class));
        businessQuestionnaireVO
                .setBusinessNodeVOS(businessQuestionnaire.getNodeList().stream().map(this::convertToVO).collect(
                        Collectors.toList()));
        return businessQuestionnaireVO;
    }

}
