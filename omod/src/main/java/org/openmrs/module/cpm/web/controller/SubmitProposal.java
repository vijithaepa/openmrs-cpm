package org.openmrs.module.cpm.web.controller;

import org.apache.commons.codec.binary.Base64;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.cpm.PackageStatus;
import org.openmrs.module.cpm.ProposedConcept;
import org.openmrs.module.cpm.ProposedConceptPackage;
import org.openmrs.module.cpm.api.ProposedConceptService;
import org.openmrs.module.cpm.web.dto.ProposedConceptDto;
import org.openmrs.module.cpm.web.dto.ProposedConceptPackageDto;
import org.openmrs.module.cpm.web.dto.SubmissionDto;
import org.openmrs.module.cpm.web.dto.SubmissionResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.nio.charset.Charset;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

//@Component
public class SubmitProposal {

	// not sure why @Autowired isn't working here, even with class being declared as a @Component
	private RestOperations submissionRestTemplate;

	public void setSubmissionRestTemplate(RestOperations submissionRestTemplate) {
		checkNotNull(submissionRestTemplate);
		this.submissionRestTemplate = submissionRestTemplate;
	}

	void submitProposedConcept(final ProposedConceptPackage conceptPackage, RestOperations submissionRestTemplate) {

		checkNotNull(submissionRestTemplate);

		//
		// Could not figure out how to get Spring to send a basic authentication request using the "proper" object approach
		// see: https://github.com/johnsyweb/openmrs-cpm/wiki/Gotchas
		//

		AdministrationService service = Context.getAdministrationService();

		SubmissionDto submission = new SubmissionDto();
		submission.setName(conceptPackage.getName());
		submission.setEmail(conceptPackage.getEmail());
		submission.setDescription(conceptPackage.getDescription());

		final ArrayList<ProposedConceptDto> list = new ArrayList<ProposedConceptDto>();
		for (ProposedConcept proposedConcept : conceptPackage.getProposedConcepts()) {
			final ProposedConceptDto conceptDto = new ProposedConceptDto();

			// concept details
			final Concept concept = proposedConcept.getConcept();
			conceptDto.setNames(ProposalController.getNameDtos(concept));
			conceptDto.setDescriptions(ProposalController.getDescriptionDtos(concept));

			ConceptDatatype conceptDatatype = concept.getDatatype();
			if (conceptDatatype != null) {
				conceptDto.setDatatype(conceptDatatype.getName());
			}
			conceptDto.setUuid(concept.getUuid());

			// proposer's comment
			conceptDto.setComment(proposedConcept.getComment());

			list.add(conceptDto);
		}
		submission.setConcepts(list);

		final HttpHeaders headers = createHeaders(service.getGlobalProperty("cpm.username"), service.getGlobalProperty("cpm.password"));
		final HttpEntity requestEntity = new HttpEntity<SubmissionDto>(submission, headers);

		final String url = service.getGlobalProperty("cpm.url") + "/ws/cpm/dictionarymanager/proposals";
		submissionRestTemplate.exchange(url, HttpMethod.POST, requestEntity, SubmissionResponseDto.class);

//		final SubmissionResponseDto result = submissionRestTemplate.postForObject("http://localhost:8080/openmrs/ws/cpm/dictionarymanager/proposals", submission, SubmissionResponseDto.class);

		conceptPackage.setStatus(PackageStatus.SUBMITTED);
		Context.getService(ProposedConceptService.class).saveProposedConceptPackage(conceptPackage);

	}

	private HttpHeaders createHeaders(final String username, final String password){
		final HttpHeaders httpHeaders = new HttpHeaders();
		String auth = username + ":" + password;
		byte[] encodedAuth = Base64.encodeBase64(
				auth.getBytes(Charset.forName("US-ASCII")));
		String authHeader = "Basic " + new String( encodedAuth );
		httpHeaders.set("Authorization", authHeader);
		return httpHeaders;
	}
}