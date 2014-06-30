// Copyright 2014 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.impala.analysis;

import org.apache.hadoop.hive.metastore.MetaStoreUtils;

import com.cloudera.impala.authorization.Privilege;
import com.cloudera.impala.catalog.AuthorizationException;
import com.cloudera.impala.common.AnalysisException;
import com.cloudera.impala.extdatasource.ApiVersion;
import com.cloudera.impala.thrift.TCreateDataSourceParams;
import com.cloudera.impala.thrift.TDataSource;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * Represents a CREATE DATA SOURCE statement.
 */
public class CreateDataSrcStmt extends StatementBase {
  private final String dataSrcName_;
  private final String className_;
  private final String apiVersionString_;
  private final HdfsUri location_;
  private final boolean ifNotExists_;
  private ApiVersion apiVersion_;

  public CreateDataSrcStmt(String dataSrcName, HdfsUri location, String className,
      String apiVersionString, boolean ifNotExists) {
    Preconditions.checkNotNull(dataSrcName);
    Preconditions.checkNotNull(className);
    Preconditions.checkNotNull(apiVersionString);
    Preconditions.checkNotNull(location);
    dataSrcName_ = dataSrcName.toLowerCase();
    location_ = location;
    className_ = className;
    apiVersionString_ = apiVersionString;
    ifNotExists_ = ifNotExists;
  }

  @Override
  public void analyze(Analyzer analyzer) throws AnalysisException,
      AuthorizationException {
    if (!MetaStoreUtils.validateName(dataSrcName_)) {
      throw new AnalysisException("Invalid data source name: " + dataSrcName_);
    }
    if (!ifNotExists_ && analyzer.getCatalog().getDataSource(dataSrcName_) != null) {
      throw new AnalysisException(Analyzer.DATA_SRC_ALREADY_EXISTS_ERROR_MSG +
          dataSrcName_);
    }

    apiVersion_ = ApiVersion.parseApiVersion(apiVersionString_);
    if (apiVersion_ == null) {
      throw new AnalysisException("Invalid API version: '" + apiVersionString_ +
          "'. Valid API versions: " + Joiner.on(", ").join(ApiVersion.values()));
    }

    location_.analyze(analyzer, Privilege.ALL);
    // TODO: Check class exists and implements API version
    // TODO: authorization check
  }

  @Override
  public String toSql() {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE DATA SOURCE ");
    if (ifNotExists_) sb.append("IF NOT EXISTS ");
    sb.append(dataSrcName_);
    sb.append(" LOCATION '");
    sb.append(location_.getLocation());
    sb.append("' CLASS '");
    sb.append(className_);
    sb.append("' API_VERSION '");
    sb.append(apiVersion_.name());
    sb.append("'");
    return sb.toString();
  }

  public TCreateDataSourceParams toThrift() {
    return new TCreateDataSourceParams(
        new TDataSource(dataSrcName_, location_.getLocation(), className_,
            apiVersion_.name())).setIf_not_exists(ifNotExists_);
  }
}
