/*
 * Copyright (c) 2026. SophonHQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict'

const { execFileSync } = require('node:child_process')

const RELEASE_LINE_BRANCH_RX = /^sophon-(\d+\.\d+)\.x$/

/**
 * Marks component versions as prereleases based on the branch they come from, so that Antora treats the newest
 * released version as the latest one. The latest version is where the site start page points, and the only version
 * in the search index.
 *
 * - A version from a release line branch (e.g. `sophon-1.1.x`) is released once a tag of that line exists
 *   (e.g. `sophon-1.1.0`), and a prerelease until then.
 * - A version from any other branch, such as `main` ("development"), is a prerelease.
 *
 * This overrides the `prerelease` key of antora.yml, which can't express this: release line branches are created from
 * main and carry the same antora.yml files.
 */
module.exports.register = function () {
  const logger = this.getLogger('prerelease-by-branch')
  const releasedLines = new Map()

  const isReleased = ({ refname, gitdir }) => {
    const line = RELEASE_LINE_BRANCH_RX.exec(refname)?.[1]
    if (!line) return false
    const key = `${gitdir}#${line}`
    if (!releasedLines.has(key)) {
      const tags = execFileSync('git', ['--git-dir', gitdir, 'tag', '--list', `sophon-${line}.*`], { encoding: 'utf8' })
      releasedLines.set(key, tags.trim() !== '')
    }
    return releasedLines.get(key)
  }

  this.on('contentAggregated', ({ contentAggregate }) => {
    for (const componentVersion of contentAggregate) {
      const { name, version, origins } = componentVersion
      const prerelease = !origins.every(isReleased)
      if (Boolean(componentVersion.prerelease) !== prerelease) {
        const refs = origins.map(({ refname }) => refname).join(', ')
        logger.info(`Marking ${name}@${version} as ${prerelease ? 'prerelease' : 'released'} (from ${refs})`)
      }
      componentVersion.prerelease = prerelease
    }
  })
}
