package biz.ixxi.calypso.service.impl;

import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import biz.ixxi.calypso.service.ReaderSamService;
import cna.sdk.seproxy.APDURequest;
import cna.sdk.seproxy.APDUResponse;
import cna.sdk.seproxy.ProxyReader;
import cna.sdk.seproxy.ReaderException;
import cna.sdk.seproxy.ReadersPlugin;
import cna.sdk.seproxy.SEProxyService;
import cna.sdk.seproxy.SERequest;
import cna.sdk.seproxy.SEResponse;

@Service("readerSamService")
public class ReaderSamServiceImpl implements ReaderSamService {

	static final Logger logger = LoggerFactory.getLogger(ReaderSamServiceImpl.class);

	@Inject
	private Environment env;

	ProxyReader reader;

	@PostConstruct
	private void init() {
		
		SEProxyService seProxyService = SEProxyService.getInstance();
		List<ReadersPlugin> listReaders = seProxyService.getPlugins();

		if (env.getProperty("calypso.sam.terminal") != null) {
			for (ReadersPlugin readersPlugin : listReaders) {
				for (ProxyReader el : readersPlugin.getReaders()) {
					if (env.getProperty("calypso.sam.terminal").equals(el.getName())) {
						this.reader = el;
						return;
					}
				}
			}
		}
	}

	@Override
	public SEResponse transmit(SERequest seRequest) {
		if (reader == null) {
			init();
		}
		SEResponse response = null;
		try {
			response = reader.transmit(seRequest);
		} catch (ReaderException e) {
			logger.error(e.getMessage(), e);
		}
		return response;
	}

	@Override
	public APDUResponse transmit(APDURequest apduRequest) {
		if (reader == null) {
			init();
		}
		APDUResponse response = null;
		try {
			SEResponse seResponse = reader
					.transmit(new SERequest(null, true, Arrays.asList(new APDURequest[] { apduRequest })));
			if (seResponse != null) {
				if (!CollectionUtils.isEmpty(seResponse.getApduResponses())) {
					response = seResponse.getApduResponses().get(0);
				}
			}

		} catch (ReaderException e) {
			logger.error(e.getMessage(), e);
		}
		return response;
	}

	@Override
	public ProxyReader getReader() {

		return reader;
	}
}
